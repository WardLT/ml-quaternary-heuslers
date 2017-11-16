import magpie.Magpie;
import magpie.data.materials.{CrystalStructureDataset, CompositionDataset, CompositionEntry};
import magpie.data.utilities.modifiers.StabilityCalculationModifier;
import magpie.data.materials.util.PropertyLists;
import java.io.PrintWriter;
import java.io.File;
import scala.collection.JavaConversions._;

// Set the number of threads
Magpie.NThreads = Runtime.getRuntime().availableProcessors();

// Create the dataset used to compute features
val dataTemplate = new CrystalStructureDataset();
dataTemplate.setDataDirectory("../magpie/lookup-data");

for (prop <- PropertyLists.getPropertySet("general")) {
    dataTemplate.addElementalProperty(prop);
}

// Load in the data
println("Loading data...");
def parseDirectory(dirName : String) : CrystalStructureDataset = {
    val myData = dataTemplate.clone().asInstanceOf[CrystalStructureDataset];
    println(s"\tLoading ${dirName}")
    
    // Load in the data
    myData.importText(s"${dirName}", null);
    println(s"\t\tRead ${myData.NEntries} entries");
    myData.setTargetProperty("delta_e", true);
    return myData;
}
val dirs = List[String]("quat-heuslers", "heuslers", "oqmd-no-heusler");
val data = dirs.zip(dirs.map(parseDirectory)).toMap

// Compute the stability for all the data
println("Computing stability...");

//   Load data as CompositionDataset
val noQHData = new CompositionDataset();
def extractCompositions(data : CrystalStructureDataset) = {
    for (ePtr <- data.getEntries()) {
        val entry = ePtr.asInstanceOf[CompositionEntry];
        val newEntry = new CompositionEntry(entry.getElements(), entry.getFractions());
        newEntry.setMeasuredClass(entry.getMeasuredClass());
        noQHData.addEntry(newEntry);
    }
}
List[String]("heuslers", "oqmd-no-heusler").map(data(_)).foreach(extractCompositions);
println(s"\tNumber of phases: ${noQHData.NEntries()}");

val stab = new StabilityCalculationModifier();
stab.setCompounds(noQHData.asInstanceOf[CompositionDataset]);
stab.setEnergyName("delta_e");
stab.setStabilityName("my_stability");

for ((n,d) <- data) {
    println(s"\tEvaluating $n");
    stab.transform(d);
}
    
// Compute attributes and save
println("Computing attributes...");
data.foreach({case (name, data) => data.generateAttributes()})

// Save in desired formats
println("Saving data...");
data.foreach({case (name, data) => data.saveCommand(s"$name", "json")});
data.foreach({case (name, data) => data.saveState(s"$name.obj")});

