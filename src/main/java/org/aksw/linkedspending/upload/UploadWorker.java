package org.aksw.linkedspending.upload;

import static org.aksw.linkedspending.tools.PropertyLoader.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;
import lombok.extern.java.Log;
import org.aksw.linkedspending.DataSetFiles;
import org.aksw.linkedspending.LinkedSpendingDatasetInfo;
import org.aksw.linkedspending.Virtuoso;
import org.aksw.linkedspending.job.Job;
import org.aksw.linkedspending.job.Phase;
import org.aksw.linkedspending.job.Worker;
import org.aksw.linkedspending.tools.DataModel;
import org.aksw.linkedspending.tools.PropertyLoader;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotReader;
import virtuoso.jena.driver.VirtGraph;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;

@Log
public class UploadWorker extends Worker
{
	/** increment when the transformation has changed and you want to recreate all datasets*/

	// version 3 components are now seperate between datasets
	// version 4 auto recognition of date properties that are not explicitly set as such
	static public final int TRANSFORMATION_VERSION = 4;

	static void uploadDataSet(String datasetName)
	{
		// delete subgraph where the old dataset resides
		Virtuoso.deleteSubGraph(datasetName);
		Virtuoso.createSubGraph(datasetName);
		//		Model model = FileManager.get().loadModel(new File(DataSetFiles.RDF_FOLDER,datasetName+".nt").getAbsolutePath());
		try(FileInputStream in = new FileInputStream(new File(DataSetFiles.RDF_FOLDER,datasetName+".nt").getAbsolutePath()))
		{
			VirtGraph virtGraph = new VirtGraph(graph+datasetName,jdbcUrl,jdbcUser,jdbcPassword);
			virtGraph.getConnection().createStatement().execute("log_enable(2,1)");
			Iterator<Triple> it = RiotReader.createIteratorTriples(in, Lang.NT, "");
			virtGraph.getBulkUpdateHandler().add(it);
			//			log.info(Duration.between(start,Instant.now()));

			// mark complete upload so that partial uploads can be detected by this missing
			virtGraph.add(Triple.create(
					Node.createURI(PropertyLoader.prefixInstance+datasetName),
					DataModel.LSOntology.uploadComplete.asNode(),
					Node.createLiteral("true",XSDDatatype.XSDboolean)));

			virtGraph.close();
			LinkedSpendingDatasetInfo.updateCache(datasetName);
		}
		catch(Exception e)
		{
			throw new RuntimeException("could not upload dataset '"+datasetName+"'"+e);
		}
	}

	public UploadWorker(String datasetName, Job job, boolean force)
	{
		super(datasetName, job, force);
	}

	@Override public Boolean get()
	{
		if(!force)
		{
			if(LinkedSpendingDatasetInfo.newestTransformation(datasetName)&&LinkedSpendingDatasetInfo.upToDate(datasetName))
			{
				String message = "newest dataset with newest transformation method already online. Skipping upload.";
				log.info(message);
				job.addHistory(message);
				job.uploadProgressPercent.set(100);
				return true;
			}
		}
		job.setPhase(Phase.UPLOAD);
		log.info("Starting upload of "+datasetName);
		uploadDataSet(datasetName);
		job.uploadProgressPercent.set(100);
		log.info("Finished upload of "+datasetName);
		return true;
	}

}