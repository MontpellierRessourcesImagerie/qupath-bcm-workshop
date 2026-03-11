import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.nio.file.Files


/**
 * Given a server name, returns the absolute path of the current image.
 */
String toFilesystemPath(String s) {
    if (s == null)
        return null

    s = s.replaceFirst(/^.*?:\s*(?=\/)/, "")
    s = s.replaceFirst(/\[.*\]$/, "")
    if (s.startsWith("file:")) {
        try {
            return new File(new URI(s)).getAbsolutePath()
        } catch (Exception e) {
            s = s.replaceFirst("^file:", "")
        }
    }

    s = URLDecoder.decode(s, StandardCharsets.UTF_8.name())
    return s
}

/**
 * Returns the path to the JSON annotation file and the target class text file.
 */
def asJsonPath() {
    def path = getCurrentImageData().getServerPath();
    path = toFilesystemPath(path);
    path = Paths.get(path);
    
    def filename = path.getFileName().toString().replace(".tif", ".json");
    filename = filename.replace(".png", ".json");
    path = path.getParent();
    path = path.getParent();
    path = path.resolve("polygons");
    path = path.resolve(filename);

    if (!Files.exists(path)) {
        print("No JSON file found at: " + path.toString());
        path = null;
    }
    return path;
}

def readObject(target) {
    if (target == null) { return null }
    def whatTxt = new File(target.toString()).getText("UTF-8")
    def lines = whatTxt.split("\n")
    red = Integer.parseInt(lines[1])
    green = Integer.parseInt(lines[2])
    blue = Integer.parseInt(lines[3])
    int rgb = red;
    rgb = (rgb << 8) + green;
    rgb = (rgb << 8) + blue;
    return [lines[0], rgb]
}

/*def parent_class = "Lung";
QP.selectObjectsByClassification(parent_class);*/

createFullImageAnnotation(true)
def objs = QP.getSelectedObjects();
if (objs.size() != 1) {
    print("Exactly one object with classification '" + parent_class + "' must exist.")
    return
}
def parent_obj = objs[0];

def jsonPath     = asJsonPath();
if (jsonPath == null) {
    print("No JSON detections file found for the current image.")
    return
}

def jsonText = new File(jsonPath.toString()).getText("UTF-8")
def gson = new Gson()
def object = gson.fromJson(jsonText, List)
def detections = [];

for (it: object) {
    def xs = it['xs'];
    def ys = it['ys'];
    def cf = it['class'];
    
    double[] xArr = xs.collect { it as double } as double[]
    double[] yArr = ys.collect { it as double } as double[]

    def roi = ROIs.createPolygonROI(xArr, yArr);
    def obj = PathObjects.createDetectionObject(roi);
    if (cf != null) {
        obj.setClassification(cf)
    }
    detections << obj;
}
parent_obj.addChildObjects(detections);
