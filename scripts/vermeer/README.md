# The Art of Painting

The gallery print uses the supplied photograph of Johannes Vermeer’s *The Art of Painting*. The paper shows through where the edges have rubbed away, following the wear on *Poppy Field*. Its reverse carries an imagined chalk study of the model and a few ruled floor lines. Both faces share a mirrored cut edge. The corner folded onto the reverse carries a little of the dark print from the front.

The studio uses the original photograph in `art/the-art-of-painting.jpg` for the figures, chandelier, curtain, map and visible furnishings. The floor’s vanishing points set the entry camera, and the intersections of the black and ivory tiles determine the pattern underfoot. The route moves just over a metre from the original viewpoint.

The supplied chess painting hangs in a shallow black-and-gold frame between the windows, above an empty oak bench. A girl in red leans against the wall between the Delft vase and the desk, looking toward the model. On the desk is a second chess scene, based on the supplied picture of two girls playing on a rug. The figures and chessboard are painted in while parts of the rug and background still show the ground. A silver ewer stands on folded linen on a side table. Across the room, a celestial globe rests on the chest. The elderly woman sits beside the open door with her folding fan, while a child peeks around the door.

`studio.py` places the ImageGen cutouts in room coordinates. The seated woman and her chair are scaled together to 95% around their floor contacts. The standing girl is scaled to 90% of the standing reference height in the reconstructed room. The door has a recessed opening and a projecting leaf. The desk has cabinet sides, a sloping board and a recessed rear stretcher. The chess study is painted directly onto the board texture so it follows the slope as the viewer turns.

To rebuild the bundled textures and geometry from the checked-in artwork, use Python with Pillow and NumPy.

```sh
python3 scripts/vermeer/build.py
```

[Preview the studio](studio-preview.jpg)

<details>
<summary>ImageGen prompts for the source artwork</summary>

#### Paper reverse

```text
Create the back of a loose paper reproduction of Vermeer's The Art of Painting as a single, high-resolution texture for a virtual museum. The attached Vermeer is a reference for the subject of a small drawing only. We have just turned over the sheet. The entire image is the material of this one leaf, straight-on, upright portrait about 9:11, cropped precisely flush to all four edges. Soft even conservation-scanner light. Quiet warm flax-grey handmade rag paper, fine erratic fibers, faint laid lines and widely spaced vertical chain lines that you almost miss, rubbed handled rim, one compressed dog-ear, a slightly darker thumb-sized handling mark. Real paper with variable tone, no noise overlay, hard crease down center, brown vignette, burn marks, heavy stains or elaborate damage.
On the verso someone once tried to work out the fall of light on the blue sleeve in this picture. One modest unfinished black-chalk drawing in the upper middle, loose life-size-to-the-sheet graphite-grey lines of the female model in profile from her laurel and downturned head to her elbow, the bent forearm cradling the large closed book from the reference. This is a seventeenth-century studio working sketch fragment, made by hand: an exploratory doubled line correcting the book angle, patches of reserved bare paper in her face, a few accurate, broad dark hatched planes in the sleeve and a whisper of rubbed blue chalk. Drawing tails off without a bottom contour. Do not draw the entire painting, an elaborate finished portrait or a trumpet floating loose. Keep most of the sheet bare, especially below the elbow. At the lower side a few very light, interrupted straight lines of a floor perspective tried by hand, barely there. No writing, labels, seals, dates, fake signatures or accessories. The visual interest is in specific anatomical observation, a handful of decisive marks, the different pressures of the chalk, and the patina of the paper itself. Restrained ochre-grey, charcoal and a hint of muted dusty blue. Flat full-bleed archival material scan, with no lighting or shadow outside the leaf, no frame, desk, stretcher or surrounding background.
```

#### Wear on the printed face

```text
Precise object edit. Image 1 is the edit target, Vermeer's The Art of Painting. Preserve his exact photograph, full composition, edges, light, colour, position of every subject and aspect ratio. Change only the outermost few millimetres into the frayed, worn face of an old art reproduction printed to the very edge on rag paper. Image 2 (landscape Poppy Field) is only the reference for how little of the edge is rubbed: a few little islands of warm old paper show through broken ink; plenty of straight intact painted edge between them. Image 3 is the actual reverse material of this specific leaf; as we turn this sheet over horizontally, its folded-over upper RIGHT corner clips the upper LEFT corner of the front painting, with a short diagonal just across the extreme tip, subtly abraded. Only other small soft corners. Make the narrow broken paint and print loss truly convincing where old fibers lift against the dark paint of Vermeer's tapestry: occasional irregular scuffed flecks exposing flax-buff stock and tiny hairline creases where the lower tip has flexed. Uneven wear from handling rather than a symmetry filter. An extremely narrow ragged rim, interrupted, a paper-thin sliver and slightly larger spalls only at corners. No added rectangular border, passepartout, white margin, frame, shadow, props, scattered stains, lettering or large areas of yellow paper. The entire portrait is flat level, straight-on, occupies the entire portrait canvas flush to the edges, never floating in a larger scene. Absolutely preserve the artwork itself even very close to the edge.
```

#### Wide studio

```text
Edit target: the attached portrait JPEG of Vermeer's The Art of Painting. OUTPAINT it into a substantially wider landscape composition, a museum-quality oil painting. Place the supplied original image centered and unscaled relative to itself in the middle 52% of a 3:2 landscape image, with its top and bottom reaching almost to the edges; keep the central original painting pixel-faithful in composition, light, detail, all people and objects. Extend only what is cut off. The heavy blue-ochre tapestry and leather chair at left need their complete outside silhouette, the table behind them ends naturally. The back wall extends left to the window jamb, and right to the edge of the antique map and a whole Spanish leather chair; the ceiling's dark parallel timber beams continue above and the black and cream checkerboard marble floor continues below. Warm chalk walls and soft lateral daylight. The entire enlarged picture is still one continuous authentic Vermeer oil painting with the same varnished pigments, precise soft edges and glints. No frame, no wall labels, no border, no other people, no new easels, no typography. Render large, landscape with fine brush detail.
```

#### Architectural reference

```text
Reference and style source: attached Vermeer, The Art of Painting. A single broad texture atlas for the unseen THREE walls of the very same Delft studio, painted as an authentic seventeenth-century Johannes Vermeer oil, use the reference's exact restrained palette, precise light falloff and soft brushwork. One wide 3:1 image consisting of THREE equal-width square panels touching edge-to-edge horizontally. Each single square is a DIFFERENT WALL seen straight-on, standing in the same room looking left, behind, then right. All three wall surfaces run from dark oak ceiling moulding very near the top edge to dark thin baseboard very near the bottom edge with only a tiny sliver of black and white marble floor. Exact straight verticals and horizontal baseboards; no receding walls. LEFT THIRD: tall intimate period leaded-glass casement window with dark wooden mullions and softly lit stone sill, cool silver daylight, creamy limewash. MIDDLE THIRD: quiet plain closed Dutch oak plank door with iron latch on the left half of the cream wall, light gently raking the plaster, a little blue-and-white Delft tile skirting. RIGHT THIRD: the most shaded warm ivory plaster wall with a small dark old wooden ledge with a few small pigment jars and earthenware, one modest empty canvas leaning against wall. Pale umber/ivory, lead white, subdued blue, deep brown, warm shadow. Paint subtle chromatic variation, optical softness, rubbed pigment, aged linen, as finely handled as the supplied original. This is a production wall texture, three images side by side, edge to edge. No painting frames around panels, no gutters, labels, writing, people, repeated map, chairs, central chandelier, dramatic tenebrism or photographic modern room.
```

#### Unobstructed portrait

```text
Precise background restoration for a layered version of the attached The Art of Painting by Vermeer. Edit the supplied image in its ORIGINAL PORTRAIT aspect ratio, camera and crop. Give me the empty room behind every foreground object: remove the blue-and-gold hanging tapestry at left, all chairs and stool, the desk with cloth and books, both people, trumpet, easel and canvas AND hanging brass chandelier. Paint through all their holes in the original Vermeer style. Retain the back wall map EXACTLY in its original size and position, including the cropped-off right edge and the left map edge at 38% canvas width. Preserve all unoccluded details and their positions. Match the ceiling timbers and warm ivory limewash, extend the unadorned plaster to left behind tapestry, faithfully continue the diamond black/ivory marble checkerboard floor in the same perspective and identical scale, back wall meets floor at about 81% image height. Camera faces front wall almost completely straight on. This is just the quiet empty studio from one exact viewpoint. Left light, luminous subdued oil paint as in the reference. Original portrait aspect. Fill frame with painting, no borders, no writing added, furniture or figures.
```

#### Marble and oak

```text
Style reference: Johannes Vermeer's attached The Art of Painting. Make an edge-to-edge 2:1 horizontal materials atlas consisting of TWO square oil paintings touching at the center, no gap, no labels or frame. Each is an orthographic face-on surface of the artist's room, with all four edges parallel, as an authentic painted reconstruction. Left square: a top-down seamless square of the Delft studio's fine seventeenth-century marble pavement, EIGHT EVEN CHECKERBOARD SQUARES ACROSS AND EIGHT DOWN of alternating warm vein-streaked ivory (soft pale buff with gray wisps) and near-black dark slate (subtle greenish umber). The checker pattern has straight vertical and horizontal edges parallel with the image borders, first tile upper left ivory; no diagonals, no perspective. Soft window illumination, almost uniform over the patch. Right square: camera aimed straight up at entire dark oak beamed ceiling. Four long evenly spaced heavy structural oak beams running horizontally left to right, narrow finer timber boards cross them vertically. True plan elevation straight up, flat picture plane. Use dark but legible walnut umbers with a few narrow worn highlights from side daylight; compare the exact reference at its top edge. On both halves: closely match Vermeer's delicate muted oil pigment, age, natural subtle irregular brushwork, realistic Dutch materials. No objects, figures, room edges or walls, no chandelier, photorealistic textures, lettering, text or glossy effects. Fill large landscape texture with exactly these two adjoining squares.
```

#### Unobstructed wide studio

```text
Edit target: supplied wide Vermeer studio painting. Create a precise SAME WIDE FRAME clean background plate by painting out all foreground subjects only: entire tied tapestry and its cord, chair, draped table and desk, blue woman, trumpet, seated painter and his stool, whole easel and canvas, right leather chair and brass chandelier. Remove their cast shadows. Restore the floor, wall, ceiling or map continuing behind each. Keep the original landscape crop and every visible architectural detail in precisely the same position: left wall's window and wall corner, map in its exact original bounds and colors, ceiling beams, and every visible checkerboard floor diamond. Absolutely no change of viewpoint or framing. Fill the large bare patch behind the tapestry with naturally lit pale wall. A single quiet luminous empty Delft studio, painted completely in the original oil painting style. No people, loose objects, added door or frames, other furniture or new decorations. Large wide 3:2 image exactly corresponding to reference.
```

#### Hidden skirt

```text
Edit target: the supplied Vermeer painting. Extract ONLY the complete standing female model in the blue dress, a production cutout on a genuine transparent background. Preserve Vermeer's exact original face, blue laurel wreath, lowered gaze, yellow book, trumpet pointing left, arms and hands, blue satin period dress, lit softly from the left, precise original pose in three-quarter view. Restore the LOWER body that was originally hidden by the foreground table: a full continuous naturally hanging bell-shaped skirt down to the FLOOR and a quiet soft hem subtly pooling, same weave, oil pigments and vertical pleated blue drapery as the original upper dress. Depict her entire figure from crown to hem and whole trumpet, plenty of transparent space around her. The ONLY subject is the woman with book and trumpet. No table, furniture, floor, architectural details, shadows on an invisible ground, outline, or background. Full length portrait.
```

#### Hidden end of the table

```text
Use the attached Vermeer The Art of Painting as exact reference. Isolate ONLY the horizontal writing table from the lower left/middle and its books, theatrical plaster mask and draped blue-grey and golden cloth, in the exact painted perspective of the original. Complete the left edge and wooden turned legs that were obscured by the heavy hanging tapestry and chair. Single 17th-century honey oak table spanning horizontally, front right turned leg clearly visible, loose dark blue-grey drapery draped vertically down its LEFT side; cream book open across the right side of the tabletop. Camera looks slightly down at it; front face nearly straight-on. Authentic Vermeer fine oil painting technique, gold-green shadow, subdued slate blue and cream, exact left daylight. True alpha cutout / transparent background. Whole table from top to feet, no chairs, tapestry, people, easel, room, floor, wall, writing or grounded shadow. This is a donor for the area formerly hidden behind the curtain. Landscape composition.
```

#### Workbench wall

```text
Reference 1 is the original Vermeer (style and lighting), reference 2 is a set of three existing studio wall studies (same room, construction materials and pigment). Render one NEW very wide 5:2 straight-on full wall elevation from inside this Delft studio, orthographic architecture, almost no floor or ceiling visible, wall fills image from the beam at top to the narrow skirting at bottom. All edges and joints should be level. Match Vermeer's cool daylight, precise color of creamy grey limewash, muted umber oak and delicate oil glazes, silvery soft light from the LEFT. No people, figurative silhouettes, central map, tapestry, easel, chandelier, signature, border or written text. This is an unframed work of painted art used as the actual wall of a navigable room, exquisite quiet visible brushwork and areas of lit/shaded plaster, no fake distress. Subject: extend the RIGHTMOST wall of the second image into a wide single composition, width 8 metres, height 3.6 metres. The LEFT half is a broad quiet patch of brighter limewash to join the map wall around the corner. Around the RIGHT OF CENTER hang a modest plain oak shelf with the same two earth pigment jars, blue-grey ceramic pot, ochre bowl. Below it, a narrow seventeenth-century oak painter's workbench with restrained still life: ochre and ultramarine pigments in shallow shells, muller and slab, linen rag. One modest empty linen-covered stretcher leaning against the far right wall. Entire visible table with its FOUR legs and air between, no cabinet. Furniture and materials should be painted at utterly believable life scale taking only around a third of the wall's width. Rectilinear composition; direct frontal wall view. Subtle contact shadows on floor. Low edge has just a hint of black-and-white diamond tiles; most of image is actual wall, no floor panorama.
```

#### Window wall

```text
Reference 1 is the original Vermeer (style and lighting), reference 2 is a set of three existing studio wall studies (same room, construction materials and pigment). Render one NEW very wide 5:2 straight-on full wall elevation from inside this Delft studio, orthographic architecture, almost no floor or ceiling visible, wall fills image from the beam at top to the narrow skirting at bottom. All edges and joints should be level. Match Vermeer's cool daylight, precise color of creamy grey limewash, muted umber oak and delicate oil glazes, silvery soft light from the LEFT. No people, figurative silhouettes, central map, tapestry, easel, chandelier, signature, border or written text. This is an unframed work of painted art used as the actual wall of a navigable room, exquisite quiet visible brushwork and areas of lit/shaded plaster, no fake distress. Subject: one broad window wall, painted very wide, twice as wide as tall at least. Along it are two substantial period oak-framed Delft leaded-glass cross-windows with pale thick deep plaster reveals and stone sills. One is close to the left third, the other is near the right third. They are related but not mirror images; one small casement is ajar, distant soft silvery daylight and a little suggestion of 17th c rooftops/green leaves through the wavy glass. Timber window frame and glazing bars are delicate. Shutters folded back, no curtains. Between the windows, calm varied pale grey-buff plaster, daylight gently falling onto a sill and draping shadow down the wall. Slightly darker at edges where the wall meets other walls. Eye is facing the wall squarely with horizontal baseboard and beam across whole bottom and top. No painting hung on wall, additional decorations, objects or furnishings. Entire front elevation of both windows with enough breathing space wall below and above. Reserve 1 percent at bottom for floor.
```

#### Door wall

```text
Reference 1 = original Vermeer The Art of Painting, painterly style and light. Reference 2 = plate of 3 existing wall studies, USE ONLY THE MIDDLE DOOR PANEL as architectural reference. Extend that door wall into one horizontal 2:1 rectangle showing its full width in the same oil painting style, frontal flat wall elevation for a 3D Delft studio. A modest oak plank door with wrought iron latch and surround in the middle. A strip of little blue-on-white period Delft baseboard tiles. To the LEFT of the door a low, dark seventeenth-century linen chest with a small folded blue-grey linen on one end. On the RIGHT side of the wall one modest, very small dark framed Dutch landscape. Well-observed quiet gray-buff plaster, raked gently by natural lateral silver daylight from the right. Deep umber timber across the very top, all edges horizontal/vertical. The entire wall is visible from the timber moulding at top to the skirting at bottom, no ceiling nor other wall, just a tiny bit of checkerboard floor at very bottom. Correct scale: the oak door is only one-sixth of image width and about two-thirds of total height, not a huge double door. Luminous optical focus and subtly varied paint like the original with gentle darks, cool reflections, no added people, hanging lights, canvases on easels, text, frames around image, theatrical lighting or exaggerated patina. No collage; one very wide horizontal image.
```

#### Woman beside the door

```text
Edit this elderly Dutch woman and her oak chair into a strictly frontal, straight-on sitting portrait, as seen by the painter directly in front of her.
Rotate and reconstruct BOTH the chair and her entire body, including her head.
The chair back is against a wall parallel to the image plane.
She sits squarely in the center of the seat against the backrest.
Both finials visible behind her, equally spaced on either side, at equal height.
The level front seat rail runs horizontally; the front legs are equally near.
Center the face, sternum, lap and chair on the same vertical line.
Her eyes look directly forward at the painter, both eyes equally visible, nose in the middle, shoulders square, knees and the toes of both slippers pointing forward.
Natural relaxed upright pose.
Retain her age, likeness, brown slippers and the gesture of the open fan beside her cheek. Her other hand rests on her lap.
Paint her gown in muted slate-blue wool with a few broad folds. Give her a plain linen cap, collar and cuffs, and a simple ochre fan.
Use the girl peeking through the door as the reference for window light, soft edges and the broad tones of the face and hands.
Deliver the complete woman AND occupied chair as one full-length cutout, with true transparent alpha around all edges and in all open spaces.
Include the soles and chair feet, viewed slightly from above the seat at adult eye level.
No floor, wall, cast shadow or other objects.
The exact frontal alignment is essential.
```

#### Foreground chair legs

```text
Edit target: The Art of Painting by Vermeer, original portrait reference. Isolate and COMPLETE the FRONTMOST leather Spanish chair whose SEAT IS CUT OFF at the BOTTOM LEFT edge of the original. Make a single chair cutout, genuine transparent background. CRITICAL: the leather seat (umber, brass nailheads, ochre-gold little fringe) is at normal chair-seat height above the floor. Four complete dark oak turned wooden legs DESCEND FROM THE SEAT, with horizontal stretchers and all feet visible. The frontmost broad seat edge is ALMOST HORIZONTAL, with right edge receding diagonally to the upper left, viewed from above, from the original eye. Its high back is on the LEFT, as in the original, not centered behind it. It is the same leather Spanish chair, painted in Vermeer's original precise soft daylight and dark pigment with softly glinting brass tacks and gilt fringe. Authentic still-life study of THIS single CHAIR in its complete form. The legs need particular detail as the region behind the bottom crop is the deliverable. No floor, wall, dark vignette, tapestry, other furniture, framing, people or text. Leave all four feet fully within the canvas.
```

</details>
