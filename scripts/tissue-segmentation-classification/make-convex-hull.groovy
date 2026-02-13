/**
 * Create a convex hull annotation surrounding all selected objects.
 * 
 * Note that this
 * - was written using QuPath v0.4.x (but probably works in some earlier releases)
 * - doesn't work very well for ellipse objects, but should be ok with other shapes
 * - assumes that you only have a 2D image (you'd need to change the 'image plane' part for z-stacks or timeseries)
 * 
 * It was written for https://forum.image.sc/t/qupath-script-command-to-draw-polygon-annotation-from-points/76833
 *
 * @author Pete Bankhead
 */

// Class of the objects used to calculate the convex hull
def input_class = "Positive"
// Class given to the convex hull annotation
def output_class = "Region*"
selectObjectsByClassification(input_class);
def selected = getSelectedObjects()
def points = []
for (def pathObject in selected) {
    points.addAll(pathObject.getROI().getAllPoints())
}
def hullPoints = qupath.lib.roi.ConvexHull.getConvexHull(points)
def roi = ROIs.createPolygonROI(hullPoints, ImagePlane.getDefaultPlane())
def hullAnnotation = PathObjects.createAnnotationObject(roi)
hullAnnotation.setClassification(output_class)
addObject(hullAnnotation)
