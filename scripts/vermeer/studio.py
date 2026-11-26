"""Register the approved interior studies to the final stop on the camera route."""

import numpy as np
from PIL import Image, ImageFilter

from atlas import pack_sprites


class _View:
    def __init__(self, eye, tilt, focal, width, yaw, pitch):
        self.eye = np.array(eye)
        self.width = width
        self.height = 992
        self.focal = focal
        turn = np.radians(yaw)
        angle = tilt + np.radians(pitch)
        self.ahead = np.array([np.sin(turn), 0, -np.cos(turn)])
        self.right = np.array([np.cos(turn), 0, np.sin(turn)])
        up = np.array([0, 1, 0])
        self.forward = self.ahead*np.cos(angle) + up*np.sin(angle)
        self.up = up*np.cos(angle) - self.ahead*np.sin(angle)

    def rays(self, x, y):
        # The approval pictures were painted over 840 x 525 renders in which
        # the centered portrait occupied 66% of the height with its center 48% down the screen.
        dx = (x/self.width - .5)*840/self.focal
        dy = (.48 - y/self.height)*525/self.focal
        return self.forward + dx[..., None]*self.right + dy[..., None]*self.up

    def intersect(self, x, y, normal, distance):
        ray = self.rays(x, y)
        t = distance / np.sum(ray*normal, axis=-1)
        # The feather around the door sill and the bottoms of the feet follow
        # the marble floor after their rays cross the wall/furniture plane.
        floor = self.eye[1]/np.maximum(-ray[..., 1], .001)
        return self.eye + ray*np.minimum(t, floor)[..., None]

    def floor_points(self, x, y):
        ray = self.rays(np.asarray(x), np.asarray(y))
        return self.eye - ray*(self.eye[1]/ray[..., 1])[..., None]

    def floor_distance(self, x, y, normal):
        return (self.floor_points(x, y) - self.eye) @ normal


def _door_recess(x, y):
    gap = (np.clip((x-677)/8, 0, 1) * np.clip((958-x)/8, 0, 1)
           * np.clip((y-140)/7, 0, 1) * np.clip((836-y)/7, 0, 1))
    # The girl's arm reaches out of the recess. Keep the palm at the same
    # depth as the fingertips clasping the free edge of the leaf.
    grip = (np.exp(-((y-524)/32)**4) * np.clip((x-683)/25, 0, 1)
            * np.clip((741-x)/20, 0, 1))
    return gap*.07*(1-grip) - .18*grip


def _desktop_depth(x, y):
    board = np.clip((522-y)/212, 0, 1)
    pot = np.clip((522-y)/56, 0, 1)
    on_board = np.clip((850-x)/20, 0, 1)
    return on_board*.48*board + (1-on_board)*.38*pot


def _panel(view, rect, texture, normal, distance, mesh, xs=(), ys=()):
    x0, y0, w, h = rect
    tx, ty, tw, th = texture
    u = np.unique(np.r_[np.linspace(x0, x0+w, tw//18+2), xs])
    v = np.unique(np.r_[np.linspace(y0, y0+h, th//18+2), ys])
    x, y = np.meshgrid(u, v)
    depth = distance(x, y) if callable(distance) else distance
    points = view.intersect(x, y, normal, depth)
    uv = np.stack((tx + (x-x0)*tw/w, ty + (y-y0)*th/h), axis=-1)
    return mesh(4, points, uv)


def _cabinet_sides(view, origin, texture, front, mesh):
    # Return both sides of the drawer case to the wall. The slim painted oak
    # posts supply the grain for the sides that appear as the viewer moves.
    ox, oy = origin
    tx, ty = texture[:2]
    sides = []
    for side, interior in ((312, 1), (969, -1)):
        u, y = np.meshgrid(np.linspace(0, 1, 6), np.linspace(536, 683, 10))
        x = np.full_like(u, side)
        points = view.intersect(x, y, view.ahead, front(x)) + .44*u[..., None]*view.ahead
        uv = np.stack((tx+side-ox+interior*(4+12*u), ty+y-oy), axis=-1)
        sides.append(mesh(4, points, uv))
    return sides


def _stretchers(view, texture, front, back, mesh):
    # The front and rear rails are in the two painted leg assemblies. Join
    # their ends with the side stretchers, using the oak on the front rail.
    tx, ty = texture[:2]
    along, down = np.meshgrid(np.linspace(0, 1, 8), np.linspace(0, 1, 3))
    sides = []
    for near, far, inset in [((356, 805), (413, 766), 10),
                             ((931, 803), (875, 765), -10)]:
        x = near[0] + (far[0]-near[0])*along
        y = near[1] + (far[1]-near[1])*along
        distance = front(x)*(1-along) + back(x)*along
        for px, py, paint_y in [(x+inset*down, y, 805+3*down),
                               (x+inset, y+15*down, 809+14*down)]:
            points = view.intersect(px, py, view.ahead, distance)
            uv = np.stack((tx+148+162*along, ty+paint_y-290), axis=-1)
            sides.append(mesh(4, points, uv))
    return sides


def _shadow(sprite, origin, offset, radius, strength):
    # Preserve the silhouette of each caster. Shadows need fewer texels than
    # the paintings and are projected onto the actual wall and floor below.
    pad = radius*3
    w, h = sprite.width+2*pad, sprite.height+2*pad
    mask = Image.new("L", (w, h))
    mask.paste(sprite.getchannel("A"), (pad, pad))
    mask = mask.resize((round(w/4), round(h/4)), Image.Resampling.LANCZOS)
    mask = mask.filter(ImageFilter.GaussianBlur(radius/4)).point(lambda a: a*strength)
    shadow = Image.new("RGBA", mask.size, (42, 33, 21))
    shadow.putalpha(mask)
    return shadow, (origin[0]+offset[0]-pad, origin[1]+offset[1]-pad, w, h)


def _furnishing(rect, texture, size, base, height, yaw, relief, mesh, wall=None):
    """Place a painted silhouette in metres, with its lowest point at the support."""
    x0, y0, w, h = rect
    tx, ty, tw, th = texture
    u, v = np.meshgrid(np.linspace(0, 1, 17), np.linspace(0, 1, 25))
    s = (x0 + u*w)/size[0]
    t = (y0 + v*h)/size[1]
    angle = np.radians(yaw)
    across = np.array([np.cos(angle), 0, np.sin(angle)])
    toward = np.array([-np.sin(angle), 0, np.cos(angle)])
    # A shallow convex face gives the silhouette some depth as the eye moves.
    depth = relief * np.maximum(0, 1-(2*s-1)**2) * np.clip(4*t*(1-t), 0, 1)
    points = (base + across*((s-.5)*height*size[0]/size[1])[..., None]
              + toward*depth[..., None])
    points[..., 1] += (1-t)*height
    if wall is not None:
        axis, coordinate = wall
        points[..., axis] = coordinate
        points[..., 1] = np.maximum(0, points[..., 1]-.015)
    uv = np.stack((tx+.5+u*(tw-1), ty+.5+v*(th-1)), axis=-1)
    return mesh(4, points, uv)


def build_studio(art, eye, tilt, focal, front, rear, right, mesh):
    origins = {"open-door": (617, 45), "door-leaf": (703, 113), "seated-woman": (629, 252),
               "artist-desk": (284, 290), "flower-vase": (1180, 250)}
    sprites = {name: Image.open(art / f"{name}.webp").convert("RGBA") for name in origins}
    rects = {name: (*origins[name], *im.size) for name, im in sprites.items()}

    door = _View(eye, tilt, focal, 1586, 169, -3)
    # Register the frontal portrait from directly across the chair. Its back
    # runs along the rear wall even when the doorway is viewed obliquely.
    chair = _View((-.65, eye[1], eye[2]), tilt, focal, 1586, 180, -3)
    desk = _View(eye, tilt, focal, 1585, 94, -4)
    vase = _View(eye, tilt, focal, 1586, -1, -.6)
    z = np.array([0, 0, 1])
    x = np.array([1, 0, 0])

    # Both front posts meet the floor at the same depth, with the two
    # slipper soles just ahead of them.
    sx, sy = origins["seated-woman"]
    foot_x = sx + np.array([20, 307, 126, 189])
    foot_y = sy + np.array([661, 661, 675, 675])
    sitter_feet = chair.floor_points(foot_x, foot_y)
    sitter_anchor = sitter_feet.mean(axis=0)
    sitter_anchor[1] = 0
    # Keep the cast shadow on the rear wall and the marble after scaling.
    shadow_anchor = np.array([sitter_anchor[0], 0, rear])
    sitter_scale = .95

    def sitter_mesh(tex, points, uv):
        return mesh(tex, sitter_anchor + (points-sitter_anchor)*sitter_scale, uv)

    def sitter_shadow_mesh(tex, points, uv):
        return mesh(tex, shadow_anchor + (points-shadow_anchor)*sitter_scale, uv)

    sprites["desk-back"] = Image.open(art / "artist-desk-back.webp").convert("RGBA")
    rects["desk-back"] = (381, 659, *sprites["desk-back"].size)
    casters = [
        ("seated-woman", chair, z, rear-eye[2], (-38, 22), 30, .27),
        ("artist-desk", desk, x, right-eye[0], (27, 66), 28, .67),
        ("flower-vase", vase, z, front-eye[2], (17, 12), 14, .38),
    ]
    for name, _, _, _, offset, blur, strength in casters:
        key = name + "-shadow"
        sprites[key], rects[key] = _shadow(sprites[name], origins[name], offset, blur, strength)

    # Heights and supports are in room metres. The chest top follows the
    # door elevation in walls.py.
    furnishings = [
        ("celestial-globe", (2.30, .87, rear-.24), .52, 149, .11, (2, rear)),
        # Match the blue model's scale in the reconstructed painting before
        # applying the approved reduction, keeping the girl's soles in place.
        ("standing-girl", (right-.19, 0, -5.36), 1.90*.90, 54, .10, (0, right)),
        ("ewer-table", (right-.41, 0, -3.44), 1.08, 70, .09, (0, right)),
    ]
    for name, *_ in furnishings:
        sprite = Image.open(art / f"{name}.webp").convert("RGBA")
        sprite = sprite.crop(sprite.getchannel("A").getbbox())
        sprites[name] = sprite
        rects[name] = (0, 0, *sprite.size)
        key = name + "-shadow"
        sprites[key], rects[key] = _shadow(sprite, (0, 0), (8, 5), 16, .21)

    atlas, textures = pack_sprites(sprites)
    layers = []
    for name, view, normal, wall, *_ in casters:
        key = name + "-shadow"
        shadow_mesh = sitter_shadow_mesh if name == "seated-woman" else mesh
        layers.append(_panel(view, rects[key], textures[key], normal, wall, shadow_mesh))

    for name, base, height, yaw, relief, wall in furnishings:
        key = name + "-shadow"
        layers.append(_furnishing(rects[key], textures[key], sprites[name].size,
                                  base, height, yaw, relief, mesh, wall))
    for name, base, height, yaw, relief, _ in furnishings:
        layers.append(_furnishing(rects[name], textures[name], sprites[name].size,
                                  base, height, yaw, relief, mesh))

    def door_plane(px, py):
        return rear-eye[2] + _door_recess(px, py)

    def leaf_plane(px, py):
        return rear-eye[2] - .01 - .17*np.clip((958-px)/(958-712), 0, 1)

    back = rear - chair.eye[2] - .06
    post, sole = sitter_feet[[0, 2], 2] - chair.eye[2]

    def sitter_plane(px, py):
        # The rails stay parallel to the wall. The woman sits against the
        # backrest and her lap projects forward between the two finials.
        row = py - sy
        frame = np.interp(row, [310, 419], [back, post])
        body = np.interp(row, [250, 405, 560], [back-.16, post, sole])
        center = sx + sprites["seated-woman"].width/2
        across_lap = np.clip((110 - np.abs(px-center))/32, 0, 1)
        return frame + across_lap*(body-frame)

    def desk_front(px):
        feet = desk.floor_distance(np.array([336, 949]), np.array([860, 857]), desk.ahead)
        return np.interp(px, [336, 949], feet)

    def desk_back(px):
        feet = desk.floor_distance(np.array([412, 879]), np.array([820, 820]), desk.ahead)
        return np.interp(px, [412, 879], feet)

    def back_plane(px, py):
        return desk_back(px)

    def desk_plane(px, py):
        return desk_front(px) + _desktop_depth(px, py)

    def vase_plane(px, py):
        foot = vase.floor_distance(1300, 717, z)
        return foot + .11*np.exp(-((px-1300)/38)**2-((py-596)/83)**2)

    layers += [
        _panel(door, rects["open-door"], textures["open-door"], z, door_plane, mesh,
               xs=(677, 685, 708, 958), ys=(140, 502, 524, 545, 835)),
        _panel(door, rects["door-leaf"], textures["door-leaf"], z, leaf_plane, mesh),
        _panel(chair, rects["seated-woman"], textures["seated-woman"], z, sitter_plane, sitter_mesh,
               xs=foot_x, ys=foot_y),
        _panel(desk, rects["desk-back"], textures["desk-back"], desk.ahead, back_plane, mesh),
        *_stretchers(desk, textures["artist-desk"], desk_front, desk_back, mesh),
        *_cabinet_sides(desk, origins["artist-desk"], textures["artist-desk"], desk_front, mesh),
        _panel(desk, rects["artist-desk"], textures["artist-desk"], desk.ahead, desk_plane, mesh),
        _panel(vase, rects["flower-vase"], textures["flower-vase"], z, vase_plane, mesh),
    ]
    return atlas, layers, sitter_anchor + (sitter_feet-sitter_anchor)*sitter_scale
