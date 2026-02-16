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
    def path = getCurrentImageData().getServerPath()
    path = toFilesystemPath(path)
    path = Paths.get(path)

    def filename = path.getFileName().toString().replace(".tif", ".json");
    path = path.getParent()
    def target = path.resolve("what.txt")
    def organ = path.getFileName()
    path = path.getParent()
    def category = path.getFileName()
    path = path.getParent()
    path = path.resolve("annotations")
    path = path.resolve(organ)
    path = path.resolve(filename)
    if (!Files.exists(path))   { path = null }
    if (!Files.exists(target)) { target = null }
    return [path, target]
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

def paths      = asJsonPath()
def jsonPath   = paths[0]
def targetPath = paths[1]

if (jsonPath == null) {
    print("No JSON annotation file found for the current image.")
    return
}

def targetClass = null
if (targetPath != null) {
    targetClass = readObject(targetPath)
    def pc = PathClass.fromString(targetClass[0], targetClass[1])
    if (getQuPath().getAvailablePathClasses().find { it.getName() == targetClass[0] } == null) {
            Platform.runLater {
                getQuPath().getAvailablePathClasses().addAll(
                    pc
                )
            }
    }
}

def jsonText = new File(jsonPath.toString()).getText("UTF-8")

def gson = new Gson()
def object = gson.fromJson(jsonText, List)

for (it: object) {
    if (it['type'] != "Feature") { continue; }
    def geo = it['geometry'];
    if (geo['type'] != "Polygon") { continue; }
    points = geo['coordinates']
    if (points.size() == 0) { continue; }
    xs = []
    ys = []
    p = points[0] // outter ring
    for (r: p) {
        xs << r[0]
        ys << r[1]
    }
    
    double[] xArr = xs.collect { it as double } as double[]
    double[] yArr = ys.collect { it as double } as double[]

    def roi = ROIs.createPolygonROI(xArr, yArr);
    def obj = PathObjects.createAnnotationObject(roi);
    if (targetClass != null) {
        obj.setClassification(targetClass[0])
    }
    addObject(obj)
}
