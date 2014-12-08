package org.aksw.linkedspending.job;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.java.Log;
import org.aksw.linkedspending.LinkedSpendingDatasetInfo;
import org.aksw.linkedspending.OpenSpendingDatasetInfo;

/** periodically gets called, starts one job if possible and then disappears again*/
@Log
public class Boss implements Runnable
{

	@Override public void run()
	{
		// must not throw any exception because ScheduledExecutorService.scheduleAtFixedRate does not schedule any more after exceptions
		String datasetName = null;
		Job job = null;
		try
		{
			log.info("Boss started");
			Map<String, LinkedSpendingDatasetInfo> lsInfos = LinkedSpendingDatasetInfo.all();
			Map<String, OpenSpendingDatasetInfo> osInfos = OpenSpendingDatasetInfo.getDatasetInfosCached();
			// first priority: unconverted datasets
			Set<String> unconverted = osInfos.keySet();
			// dont choose one that is already being worked on
			unconverted.removeAll(Job.all());
			unconverted.removeAll(lsInfos.keySet());

			if(!unconverted.isEmpty())
			{
				datasetName = unconverted.iterator().next();
				log.info("Boss starting unconverted dataset "+datasetName);
			} else // are there already converted but outdated ones?
			{
				Set<String> converted = osInfos.keySet();
				converted.removeAll(unconverted);
				Set<String> outdated = converted.stream().filter(s->LinkedSpendingDatasetInfo.isUpToDate(s)).collect(Collectors.toSet());

				if(!outdated.isEmpty())
				{
					datasetName = outdated.iterator().next();
					log.info("Boss starting outdated dataset "+datasetName);
				}
			}
			if(datasetName!=null)
			{
				job = Job.forDatasetOrCreate(datasetName);
				boolean finished = new DownloadConvertUploadWorker(datasetName, job, true).get();
				if(finished)
				{
					// TODO check for memory leaks (references)
					Job.jobs.remove(job);
				}
			}
		}
		catch(Exception e)
		{
			if(job!=null)
			{
				job.setState(State.FAILED);
				job.addHistory(e.getMessage()+": "+Arrays.toString(e.getStackTrace()));
			}
			log.severe("Dataset "+datasetName+": Unexpected exception: "+e.getMessage());
			e.printStackTrace();
		}
	}

}