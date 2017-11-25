import magpie.Magpie;
import magpie.models.classification.CumulantExpansionClassifier;
import magpie.data.materials.PrototypeEntry;
import magpie.data.materials.PrototypeDataset;
import magpie.data.materials.util.PrototypeSiteInformation;
import magpie.optimization.rankers.ClassProbabilityRanker;
import scala.collection.JavaConverters._;
import java.util.{List => JList};
import java.io.PrintWriter;
import scala.io.Source;

// Adjustable settings
Magpie.NThreads = 4;
val train_size = 1000;
val test_size = 20000;
val n_repeats = 25;
val pick_size = 50;

// Make QH entry type

var qhSiteInfo = new PrototypeSiteInformation();
for (i <- 0 to 3) {
    qhSiteInfo.addSite(1, true, List[Integer]().asJava);
}
    
class QuatHeusler(siteInfo : PrototypeSiteInformation, comp : String) extends 
    PrototypeEntry(siteInfo : PrototypeSiteInformation, comp : String) {
	
	override def getEquivalentPrototypes() : JList[PrototypeEntry] = {
		var output = List[PrototypeEntry]();
		// Generate list
		for (i <- 0 to 3) {
			val eq1 = clone();
			val eq2 = clone();
			for (j <- 0 to 3) {
				eq1.setSiteComposition((j+i) % 4, getSiteComposition(j));
				eq2.setSiteComposition((j+i) % 4, getSiteComposition(3-j));
				output = eq1 :: eq2 :: output;
			}
		}
		return output.asJava;
	}
}

// Read in training data
val qh_data = new PrototypeDataset();
qh_data.setClassNames(Array[String]("Stable","Unstable"));
def read_point(composition : String, stable : String) = {
    val x = new QuatHeusler(qhSiteInfo, composition);
    x.setMeasuredClass(if (stable == "True") 0 else 1);
    qh_data.addEntry(x);
}
Source.fromFile("qh-training-set.list").getLines.drop(1).map(_.split(" ")).foreach(x => read_point(x(0), x(1)));

// Train the ML model
val model = new CumulantExpansionClassifier();
model.setNBins(Array[Int](30,120,120));
model.setNComponents(4);
model.defineKnownCompounds("../label-prototypes/oqmd_prototypes.list");

// Run CV tests
var scores = List[Int]();
val ranker = new ClassProbabilityRanker("Stable");
ranker.setUseMeasured(false);
ranker.setMaximizeFunction(true);
for (test <- 0 until n_repeats) {
	val full_data = qh_data.clone();
	val train_data = full_data.getRandomSplit(train_size);
	val test_data = full_data.getRandomSplit(test_size);
	
	model.train(train_data);
	model.run(test_data);
	
	ranker.train(test_data);
	ranker.sortByRanking(test_data);
	
	var n_hits = 0;
	for (i <- 0 until pick_size) {
		if (test_data.getEntry(i).getMeasuredClass() == 0) {
			n_hits += 1;
		}
	}
	scores = n_hits :: scores;
	println(s"$test: $n_hits")
}

// print results to a JSON-like file
val fp = new PrintWriter("dmsp.json");
fp.print("[");
fp.print(scores.map(x => s"$x").mkString(","))
fp.println("]");
fp.close();
