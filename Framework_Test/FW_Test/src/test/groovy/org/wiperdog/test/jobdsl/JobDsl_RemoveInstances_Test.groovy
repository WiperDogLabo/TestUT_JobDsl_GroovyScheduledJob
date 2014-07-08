package org.wiperdog.test.jobdsl

import static org.junit.Assert.*
import static org.ops4j.pax.exam.CoreOptions.*
import groovy.lang.GroovyClassLoader;

import javax.inject.Inject

import static org.junit.Assert.*
import static org.ops4j.pax.exam.CoreOptions.*

import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.ops4j.pax.exam.Configuration
import org.ops4j.pax.exam.Option
import org.ops4j.pax.exam.junit.PaxExam
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy
import org.ops4j.pax.exam.spi.reactors.PerMethod
import org.ops4j.pax.exam.spi.reactors.PerClass
import org.junit.runner.JUnitCore
import org.osgi.service.cm.ManagedService
import org.codehaus.groovy.tools.RootLoader

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JobDsl_RemoveInstances_Test {
	public static final String PATH_TO_JOBCLASS     = "src/groovy/GroovyScheduledJob.groovy"
	public static final String PATH_TO_JOBDSLCLASS  = "src/groovy/JobDsl.groovy"

	String path = System.getProperty("user.dir")
	def jf
	Class jobExecutableCls
	Class jobDslCls
	def shell
	def binding
	def jobDslInst
	def groovyScheduleJobObj

	public InterruptJobTest() {
	}

	@Inject
	private org.osgi.framework.BundleContext context;

	@Configuration
	public Option[] config() {
		return options(
		cleanCaches(true),
		frameworkStartLevel(6),
		// felix log level
		systemProperty("felix.log.level").value("4"), // 4 = DEBUG
		// setup properties for fileinstall bundle.
		systemProperty("felix.home").value(path),
		systemProperty("org.quartz.scheduler.skipUpdateCheck").value("true"),
		systemProperty("org.quartz.threadPool.threadCount").value("20"),
		systemProperty("org.quartz.threadPool.class").value("org.quartz.simpl.SimpleThreadPool"),
		systemProperty("org.quartz.threadPool.threadPriority").value("5"),
		systemProperty("org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread").value("true"),
		// Pax-exam make this test code into OSGi bundle at runtime, so
		// we need "groovy-all" bundle to use this groovy test code.
		mavenBundle("org.codehaus.groovy", "groovy-all", "2.2.1").startLevel(2),
		mavenBundle("commons-collections", "commons-collections", "3.2.1").startLevel(2),
		mavenBundle("commons-beanutils", "commons-beanutils", "1.8.0").startLevel(2),
		mavenBundle("commons-digester", "commons-digester", "2.0").startLevel(2),
		wrappedBundle(mavenBundle("c3p0", "c3p0", "0.9.1.2").startLevel(3)),
		mavenBundle("org.wiperdog", "org.wiperdog.rshell.api", "0.1.0").startLevel(3),
		mavenBundle("org.quartz-scheduler", "quartz", "2.2.1").startLevel(3),
		mavenBundle("org.wiperdog", "org.wiperdog.configloader", "0.1.0").startLevel(3),
		mavenBundle("org.wiperdog", "org.wiperdog.directorywatcher", "0.1.0").startLevel(3),
		mavenBundle("org.wiperdog", "org.wiperdog.rshell.api", "0.1.0").startLevel(3),
		mavenBundle("org.wiperdog", "org.wiperdog.scriptsupport.groovyrunner", "0.2.0").startLevel(3),
		mavenBundle("org.wiperdog", "org.wiperdog.jobmanager", "0.2.3-SNAPSHOT").startLevel(3),
		junitBundles()
		);
	}

	@Before
	public void setup() throws Exception {
		jf = context.getService(context.getServiceReference("org.wiperdog.jobmanager.JobFacade"));
		URL [] scriptpath123 = [
			new File(path + "/src/groovy").toURI().toURL()
		]
		// Load class using the inherit class loader from parent class loader

		ClassLoaderUtil lc = new ClassLoaderUtil();
		lc.addURL(scriptpath123);
		println "***** Start loading reference groovy classes"
		try{
			jobExecutableCls = lc.getCls(PATH_TO_JOBCLASS)
			jobDslCls = lc.getCls(PATH_TO_JOBDSLCLASS)
			//lc.getCls(PATH_TO_JOBEXCLASS)
			//-- Setting Groovy shell
			binding = new Binding()
			binding.setVariable("felix_home", path)
			RootLoader rootloader = new RootLoader(scriptpath123, lc.getClzzLoader())
			shell = new GroovyShell(rootloader,binding)
			jobDslInst = jobDslCls.newInstance(shell, jf, context)
		}catch(Exception e){
			println "***** ERROR: " + e
		}
		println "***** Complete setup phase!"
	}

	@After
	public void shutdown() throws Exception {
		jf = null
		jobExecutableCls = null
		jobDslCls = null
		shell = null
		binding = null
		jobDslInst = null
		groovyScheduleJobObj = null
	}

	/**
	 * Instances running (exists: instances, job | not exists: jobclass, trigger)
	 * Instances has define the schedule.
	 * Job has not define jobclass
	 * 
	 * Expected:
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in jobfacade was removed
	 */
	@Test
	public void removeInstances_01()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_01/testJob_01.job")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_01/testJob_01.instances")
		// Prepare data test
		shell = new GroovyShell()
		def listInstances = []
		def textParsed = ""
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		def instfilename = instanceFile.getName()
		def instEval = shell.evaluate(instanceFile)
		def jobName = instanceFile.getName().substring(0, instanceFile.getName().indexOf(".instances"))
		instEval.each {
			def mapInstances = [:]
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		jobDslInst.mapInstFileListInsts[instfilename] = []
		listInstances.each {element_listinst ->
			def triggerInstances
			def isNotSchedule = true
			def job_inst = jobName + "_" + element_listinst.instancesName
			def tmpTextParesd = textParsed.trim()
			jobDslInst.mapInstFileListInsts[instfilename].add(job_inst)
			// Parse class and create job instances
			tmpTextParesd = tmpTextParesd.replace(jobName,job_inst)
			def clsJob = jobDslInst.loader.parseClass(tmpTextParesd, job_inst)
			def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, element_listinst.params, jobName, element_listinst.instancesName, jobDslInst.sender)
			def jfJob = jf.createJob(scheduledJob)
			
			long interval = Long.parseLong(element_listinst.schedule.substring(0,element_listinst.schedule.lastIndexOf('i')))*1000
			println "Create trigger for job: " + jobName + "_" + element_listinst.instancesName + " with interval: " + interval + "ms"
			def jfTrg = jf.createTrigger(jobName + "_" + element_listinst.instancesName, 0, interval)
			println "Trigger was created !!!"
			jf.scheduleJob(jfJob, jfTrg)
		}
		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_01.instances'])
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_01'])
		assertEquals(null, jf.getJob("testJob_01_inst_1"))
		assertEquals(null, jf.getTrigger("testJob_01_inst_1"))
	}

	/**
	 * Instances running (exists: instances, job, jobclass | not exists: trigger)
	 * Instances has define the schedule.
	 * Job has define the class
	 *
	 * Expected:
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in mapJobInCls was removed
	 * 		instances data in jobfacade was removed
	 *
	 */
	@Test
	public void removeInstances_02()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_02/testJob_02.job")
		File clsFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_02/testJob_02.cls")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_02/testJob_02.instances")
		// Prepare data test
		shell = new GroovyShell()
		jobDslInst.loader.clearCache()
		def clsJob = jobDslInst.loader.parseClass(jobFile)
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, jobDslInst.sender)
		def jobName = scheduledJob.getJobName()
		def jobClassName = scheduledJob.getJobClassName()
		def listInstances = []
		def textParsed = ""
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		def instfilename = instanceFile.getName()
		def instEval = shell.evaluate(instanceFile)
		def listJobWaitCls = []
		listJobWaitCls.add(jobName)
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listJobWaitCls.add(jobName + "_" + it.key)
			long interval = Long.parseLong(it.value.schedule.substring(0,it.value.schedule.lastIndexOf('i')))*1000
			println "Create trigger for job: " + jobName + "_" + it.key + " with interval: " + interval + "ms"
			println jf.createTrigger(jobName + "_" + it.key, 0, interval)
			println "Trigger was created !!!"
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		jobDslInst.mapJobInCls = [:]
		jobDslInst.mapJobInCls[jobClassName] = listJobWaitCls
		jobDslInst.mapInstFileListInsts[instfilename] = []
		listInstances.each {element_listinst ->
			def triggerInstances
			def isNotSchedule = true
			def job_inst = jobName + "_" + element_listinst.instancesName
			def tmpTextParesd = textParsed.trim()
			jobDslInst.mapInstFileListInsts[instfilename].add(job_inst)
			// tmpTextParesd != null means .job file is loaded
			if(tmpTextParesd != null) {
				// Parse class and create job instances
				if(tmpTextParesd.contains(jobName)) {
					tmpTextParesd = tmpTextParesd.replace(jobName,job_inst)
				}
				def clsJobInst = jobDslInst.loader.parseClass(tmpTextParesd, job_inst)
				def scheduledJobInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, element_listinst.params, jobName, element_listinst.instancesName, jobDslInst.sender)
				try {
					jf.createJob(scheduledJobInst)
				} catch (Exception e) {
					println "Error createJob: " + e
				}
			}
		}
		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		// Assert mapInstFileListInsts
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_02.instances'])
		// Assert mapJobListInstances
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_02'])
		// Assert mapJobInCls
		listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobInClsAfter = [:]
		mapJobInClsAfter[jobClassName] = listJobWaitCls
		assertEquals(mapJobInClsAfter, jobDslInst.mapJobInCls)
		// Assert job detail & trigger
		assertEquals(null, jf.getJob("testJob_02_inst_1"))
		assertEquals(null, jf.getTrigger("testJob_02_inst_1"))
	}

	/**
	 * Instances running (exists: instances, job, trigger | not exists: jobclass)
	 * Instances has not define the schedule.
	 * Job has not define jobclass
	 * 
	 * Expected:
	 * 		instances data in mapJobDefaultSchedule was removed
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in jobfacade was removed
	 *
	 */
	@Test
	public void removeInstances_03()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_03/testJob_03.job")
		File trgFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_03/testJob_03.trg")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_03/testJob_03.instances")
		// Prepare data test
		// mapJobDefaultSchedule
		def mapJobDefaultSchedule = [:]
		shell = new GroovyShell()
		trgFile.eachLine { aline ->
			def trg = shell.evaluate( "[" + aline + "]")
			def jobname = trg['job']
			def defaultSchedule = trg['schedule']
			mapJobDefaultSchedule[jobname] = defaultSchedule
		}
		jobDslInst.mapJobDefaultSchedule = mapJobDefaultSchedule
		// mapInstFileListInsts
		def listInst = []
		listInst.add("testJob_03_inst_1")
		jobDslInst.mapInstFileListInsts['testJob_03.instances'] = listInst
		// mapJobListInstances
		def tmpMap = [:]
		def listInstances = []
		tmpMap.put("instancesName", "inst_1")
		tmpMap.put("schedule", null)
		tmpMap.put("params", null)
		listInstances.add(tmpMap)
		jobDslInst.mapJobListInstances['testJob_03'] = listInstances
		// Job Detail
		def textParsed = ""
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		def tmpTextParesd = textParsed.trim()
		tmpTextParesd = tmpTextParesd.replace("testJob_03","testJob_03_inst_1")
		def clsJob = jobDslInst.loader.parseClass(tmpTextParesd, "testJob_03_inst_1")
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, null, "testJob_03", "inst_1", jobDslInst.sender)
		jf.createJob(scheduledJob)
		// Trigger Detail
		jf.createTrigger("testJob_03_inst_1", 0, 60000)
		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		assertEquals("60i", jobDslInst.mapJobDefaultSchedule['testJob_03'])
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_03.instances'])
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_03'])
		assertEquals(null, jf.getJob("testJob_03_inst_1"))
		assertEquals(null, jf.getTrigger("testJob_03_inst_1"))
	}

	/**
	 * Instances running (exists: instances, job, trigger, jobclass | not exists: )
	 * Instances has not define the schedule.
	 * Job has define jobclass
	 * 
	 * Expected:
	 * 		instances data in mapJobDefaultSchedule was removed
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in jobfacade was removed
	 */
	@Test
	public void removeInstances_04()throws Exception {
		File clsFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_04/testJob_04.cls")
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_04/testJob_04.job")
		File trgFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_04/testJob_04.trg")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_04/testJob_04.instances")
		// Prepare data test
		// mapJobDefaultSchedule
		def mapJobDefaultSchedule = [:]
		shell = new GroovyShell()
		trgFile.eachLine { aline ->
			def trg = shell.evaluate( "[" + aline + "]")
			def jobname = trg['job']
			def defaultSchedule = trg['schedule']
			mapJobDefaultSchedule[jobname] = defaultSchedule
		}
		jobDslInst.mapJobDefaultSchedule = mapJobDefaultSchedule
		// mapInstFileListInsts
		def listInst = []
		listInst.add("testJob_04_inst_1")
		jobDslInst.mapInstFileListInsts['testJob_04.instances'] = listInst
		// mapJobListInstances
		def tmpMap = [:]
		def listInstances = []
		tmpMap.put("instancesName", "inst_1")
		tmpMap.put("schedule", null)
		tmpMap.put("params", null)
		listInstances.add(tmpMap)
		jobDslInst.mapJobListInstances['testJob_04'] = listInstances
		// Job Detail
		def textParsed = ""
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		def tmpTextParesd = textParsed.trim()
		tmpTextParesd = tmpTextParesd.replace("testJob_04","testJob_04_inst_1")
		def clsJob = jobDslInst.loader.parseClass(tmpTextParesd, "testJob_04_inst_1")
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, null, "testJob_04", "inst_1", jobDslInst.sender)
		jf.createJob(scheduledJob)
		// Trigger Detail
		jf.createTrigger("testJob_04_inst_1", 0, 60000)
		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		assertEquals("60i", jobDslInst.mapJobDefaultSchedule['testJob_04'])
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_04.instances'])
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_04'])
		assertEquals(null, jf.getJob("testJob_04_inst_1"))
		assertEquals(null, jf.getTrigger("testJob_04_inst_1"))
	}

	/**
	 * Instances stopped: wait job (exists: instances | not exists: job, jobclass, trigger)
	 * Instances has define the schedule.
	 *
	 * Expected: 
	 *		instances data in mapJobListInstances was removed
	 *		instances data in mapInstancesWaitJob was removed
	 *		instances data in mapInstFileListInsts was removed
	 */
	@Test
	public void removeInstances_05_1()throws Exception {
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_05/testJob_05_1.instances")
		// Prepare data test
		def listInstances = []
		// Evaluate file .instances
		shell = new GroovyShell()
		def instEval = shell.evaluate(instanceFile)
		def jobName = instanceFile.getName().substring(0, instanceFile.getName().indexOf(".instances"))
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		jobDslInst.mapInstancesWaitJob[jobName] = instanceFile
		assertEquals("10i", listInstances[0]['schedule'])
		// mapInstFileListInsts
		def listInst = []
		listInst.add("testJob_05_1_inst_1")
		jobDslInst.mapInstFileListInsts['testJob_05_1.instances'] = listInst
		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_05_1'])
		assertEquals(null, jobDslInst.mapInstancesWaitJob['testJob_05_1'])
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_05_1.instances'])
	}

	/**
	 * Instances wait job (exists: instances | not exists: job, jobclass, trigger)
	 * Instances has not define the schedule.
	 *
	 * Expected:
	 * 		instances data in mapJobListInstances was removed
	 *		instances data in mapInstancesWaitJob was removed
	 *		instances data in mapInstFileListInsts was removed
	 */
	@Test
	public void removeInstances_05_2()throws Exception {
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_05/testJob_05_2.instances")
		// Prepare data test
		def listInstances = []
		// Evaluate file .instances
		shell = new GroovyShell()
		def instEval = shell.evaluate(instanceFile)
		def jobName = instanceFile.getName().substring(0, instanceFile.getName().indexOf(".instances"))
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		jobDslInst.mapInstancesWaitJob[jobName] = instanceFile
		assertEquals(null, listInstances[0]['schedule'])
		// mapInstFileListInsts
		def listInst = []
		listInst.add("testJob_05_2_inst_1")
		jobDslInst.mapInstFileListInsts['testJob_05_2.instances'] = listInst
		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_05_2'])
		assertEquals(null, jobDslInst.mapInstancesWaitJob['testJob_05_2'])
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_05_2.instances'])
	}

	/**
	 * Instances running (exists: instances, job | not exists: jobclass, trigger)
	 * Instances has define the schedule.
	 * Job has define job class
	 * 
	 * Expected:
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in mapJobInCls was removed
	 * 		instances data in lstJobWaitJobClass was removed
	 * 		instances data in lstTriggerWaitAll was removed
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in jobfacade was removed
	 *
	 */
	@Test
	public void removeInstances_06_1()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_06/testJob_06_1.job")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_06/testJob_06_1.instances")
		def listInstances = []
		shell = new GroovyShell()
		// Prepare data test
		jobDslInst.loader.clearCache()
		def clsJob = jobDslInst.loader.parseClass(jobFile)
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, jobDslInst.sender)
		def jobName = scheduledJob.getJobName()
		def jobClassName = scheduledJob.getJobClassName()
		// mapJobListInstances, mapJobInCls, lstJobWaitJobClass
		def listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobWaitClass = [:]
		mapJobWaitClass['jobClass'] = jobClassName
		mapJobWaitClass['jobName'] = jobName
		jobDslInst.lstJobWaitJobClass = []
		jobDslInst.lstJobWaitJobClass.add(mapJobWaitClass)
		def instEval = shell.evaluate(instanceFile)
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listJobWaitCls.add(jobName + "_" + it.key)
			def mapJobWaitClassInst = [:]
			mapJobWaitClassInst['jobClass'] = jobClassName
			mapJobWaitClassInst['jobName'] = jobName + "_" + it.key
			jobDslInst.lstJobWaitJobClass.add(mapJobWaitClassInst)
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		jobDslInst.mapJobInCls = [:]
		jobDslInst.mapJobInCls[jobClassName] = listJobWaitCls
		// mapInstFileListInsts
		def listInst = []
		listInst.add("testJob_06_1_inst_1")
		jobDslInst.mapInstFileListInsts['testJob_06_1.instances'] = listInst
		// List trigger wait all
		jobDslInst.lstTriggerWaitAll = []
		def mapTriggerWaitAll = [:]
		mapTriggerWaitAll["trigger"] = jf.createTrigger("testJob_06_1_inst_1", 0, 10000)
		mapTriggerWaitAll["jobName"] = "testJob_06_1_inst_1"
		mapTriggerWaitAll["jobClass"] = "CLASS_A"
		jobDslInst.lstTriggerWaitAll.add(mapTriggerWaitAll)
		// Job Detail
		def textParsed = ""
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		def tmpTextParesd = textParsed.trim()
		tmpTextParesd = tmpTextParesd.replace("testJob_06_1","testJob_06_1_inst_1")
		def clsJobInst = jobDslInst.loader.parseClass(tmpTextParesd, "testJob_06_1_inst_1")
		def scheduledJobInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJobInst, null, "testJob_06_1", "inst_1", jobDslInst.sender)
		jf.createJob(scheduledJobInst)
		//		Data test:
		//		mapJobListInstances: [testJob_06_1:[[instancesName:inst_1, schedule:10i, params:null]]]
		//		mapJobInCls: [CLASS_A:[testJob_06_1, testJob_06_1_inst_1]]
		//		lstJobWaitJobClass: [[jobClass:CLASS_A, jobName:testJob_06_1], [jobClass:CLASS_A, jobName:testJob_06_1_inst_1]]

		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		// Assert mapJobListInstances
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_06_1'])
		// Assert mapJobInCls
		listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobInClsAfter = [:]
		mapJobInClsAfter[jobClassName] = listJobWaitCls
		assertEquals(mapJobInClsAfter, jobDslInst.mapJobInCls)
		// Assert lstJobWaitJobClass
		def lstJobWaitJobClassAfter = []
		mapJobWaitClass = [:]
		mapJobWaitClass['jobClass'] = jobClassName
		mapJobWaitClass['jobName'] = jobName
		lstJobWaitJobClassAfter.add(mapJobWaitClass)
		assertEquals(lstJobWaitJobClassAfter, jobDslInst.lstJobWaitJobClass)
		// Assert lstTriggerWaitAll
		assertEquals([], jobDslInst.lstTriggerWaitAll)
		// Assert mapInstFileListInsts
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_06_1.instances'])
		// Assert job detail
		assertEquals(null, jf.getJob("testJob_06_1_inst_1"))
	}

	/**
	 * Instances running (exists: instances, job | not exists: jobclass, trigger)
	 * Instances has not define the schedule.
	 * Job has define job class
	 * 
	 * Expected:
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in mapJobInCls was removed
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in jobfacade was removed
	 */
	//@Test
	public void removeInstances_06_2()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_06/testJob_06_1.job")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_06/testJob_06_2.instances")
		def listInstances = []
		shell = new GroovyShell()
		// Prepare data test
		jobDslInst.loader.clearCache()
		def clsJob = jobDslInst.loader.parseClass(jobFile)
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, jobDslInst.sender)
		def jobName = scheduledJob.getJobName()
		def jobClassName = scheduledJob.getJobClassName()
		// mapJobListInstances, mapJobInCls, lstJobWaitJobClass
		def listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobWaitClass = [:]
		mapJobWaitClass['jobClass'] = jobClassName
		mapJobWaitClass['jobName'] = jobName
		jobDslInst.lstJobWaitJobClass = []
		jobDslInst.lstJobWaitJobClass.add(mapJobWaitClass)
		def instEval = shell.evaluate(instanceFile)
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listJobWaitCls.add(jobName + "_" + it.key)
			def mapJobWaitClassInst = [:]
			mapJobWaitClassInst['jobClass'] = jobClassName
			mapJobWaitClassInst['jobName'] = jobName + "_" + it.key
			jobDslInst.lstJobWaitJobClass.add(mapJobWaitClassInst)
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		jobDslInst.mapJobInCls = [:]
		jobDslInst.mapJobInCls[jobClassName] = listJobWaitCls
		// mapInstFileListInsts
		def listInst = []
		listInst.add("testJob_06_2_inst_1")
		jobDslInst.mapInstFileListInsts['testJob_06_2.instances'] = listInst
		// Job Detail
		def textParsed = ""
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		def tmpTextParesd = textParsed.trim()
		tmpTextParesd = tmpTextParesd.replace("testJob_06_2","testJob_06_2_inst_1")
		def clsJobInst = jobDslInst.loader.parseClass(tmpTextParesd, "testJob_06_2_inst_1")
		def scheduledJobInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJobInst, null, "testJob_06_2", "inst_1", jobDslInst.sender)
		jf.createJob(scheduledJobInst)
		//		Data test:
		//		mapJobListInstances: [testJob_06_1:[[instancesName:inst_1, schedule:null, params:null]]]
		//		mapJobInCls: [CLASS_A:[testJob_06_1, testJob_06_1_inst_1]]
		//		lstJobWaitJobClass: [[jobClass:CLASS_A, jobName:testJob_06_1], [jobClass:CLASS_A, jobName:testJob_06_1_inst_1]]

		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		// Assert mapJobListInstances
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_06_1'])
		// Assert mapJobInCls
		listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobInClsAfter = [:]
		mapJobInClsAfter[jobClassName] = listJobWaitCls
		assertEquals(mapJobInClsAfter, jobDslInst.mapJobInCls)
		// Assert lstJobWaitJobClass
		def lstJobWaitJobClassAfter = []
		mapJobWaitClass = [:]
		mapJobWaitClass['jobClass'] = jobClassName
		mapJobWaitClass['jobName'] = jobName
		lstJobWaitJobClassAfter.add(mapJobWaitClass)
		assertEquals(lstJobWaitJobClassAfter, jobDslInst.lstJobWaitJobClass)
		// Assert mapInstFileListInsts
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_06_2.instances'])
		// Assert job detail
		assertEquals(null, jf.getJob("testJob_06_2_inst_1"))
	}

	/**
	 * Instances running (exists: instances, job | not exists: jobclass, trigger)
	 * Instances has not define the schedule.
	 * Job has not define job class
	 * 
	 * Expected:
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in jobfacade was removed
	 */
	@Test
	public void removeInstances_06_3()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_06/testJob_06_3.job")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_06/testJob_06_3.instances")
		def listInstances = []
		shell = new GroovyShell()
		// Prepare data test
		jobDslInst.loader.clearCache()
		def clsJob = jobDslInst.loader.parseClass(jobFile)
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, jobDslInst.sender)
		def jobName = scheduledJob.getJobName()
		def jobClassName = scheduledJob.getJobClassName()
		// mapJobListInstances
		def instEval = shell.evaluate(instanceFile)
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		// mapInstFileListInsts
		def listInst = []
		listInst.add("testJob_06_3_inst_1")
		jobDslInst.mapInstFileListInsts['testJob_06_3.instances'] = listInst
		// Job Detail
		def textParsed = ""
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		def tmpTextParesd = textParsed.trim()
		tmpTextParesd = tmpTextParesd.replace("testJob_06_3","testJob_06_3_inst_1")
		def clsJobInst = jobDslInst.loader.parseClass(tmpTextParesd, "testJob_06_3_inst_1")
		def scheduledJobInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJobInst, null, "testJob_06_3", "inst_1", jobDslInst.sender)
		jf.createJob(scheduledJobInst)
		//		Data test:
		//		mapJobListInstances: [testJob_06_3:[[instancesName:inst_1, schedule:null, params:null]]]

		// Remove Instances
		jobDslInst.removeInstances(instanceFile)
		// Assert mapJobListInstances
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_06_3'])
		// Assert mapInstFileListInsts
		assertEquals(null, jobDslInst.mapInstFileListInsts['testJob_06_3.instances'])
		// Assert job detail
		assertEquals(null, jf.getJob("testJob_06_3_inst_1"))
	}

	/**
	 * Instances running (exists: instances, job, trigger | not exists: jobclass)
	 * Instances has define the schedule.
	 * Job has define job class
	 * 
	 * Expected:
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in mapJobInCls was removed
	 * 		instances data in lstJobWaitJobClass was removed
	 * 		instances data in lstTriggerWaitAll was removed
	 * 		instances data in lstTriggerWaitJob was removed
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in jobfacade was removed
	 */
	@Test
	public void removeInstances_07_1()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_07/testJob_07_1.job")
		File trgFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_07/testJob_07_1.trg")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_07/testJob_07_1.instances")
		def listInstances = []
		shell = new GroovyShell()
		// Prepare data test
		jobDslInst.loader.clearCache()
		def clsJob = jobDslInst.loader.parseClass(jobFile)
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, jobDslInst.sender)
		def jobName = scheduledJob.getJobName()
		def jobClassName = scheduledJob.getJobClassName()
		// mapJobListInstances, mapJobInCls, lstJobWaitJobClass
		def listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobWaitClass = [:]
		mapJobWaitClass['jobClass'] = jobClassName
		mapJobWaitClass['jobName'] = jobName
		jobDslInst.lstJobWaitJobClass = []
		jobDslInst.lstJobWaitJobClass.add(mapJobWaitClass)
		def instEval = shell.evaluate(instanceFile)
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listJobWaitCls.add(jobName + "_" + it.key)
			def mapJobWaitClassInst = [:]
			mapJobWaitClassInst['jobClass'] = jobClassName
			mapJobWaitClassInst['jobName'] = jobName + "_" + it.key
			jobDslInst.lstJobWaitJobClass.add(mapJobWaitClassInst)
			listInstances.add(mapInstances)
		}
		jobDslInst.mapJobListInstances[jobName] = listInstances
		jobDslInst.mapJobInCls = [:]
		jobDslInst.mapJobInCls[jobClassName] = listJobWaitCls

		trgFile.eachLine { aline ->
			def trg = shell.evaluate( "[" + aline + "]")
			def jobname = trg['job']
			def defaultSchedule = trg['schedule']
			// Create trigger and job original
			def trigger_ori = jf.createTrigger(jobName, 0, 60000)
			def job_ori = jf.getJob(jobname)
			if (trigger_ori != null && defaultSchedule != 'delete') {
				if (job_ori == null) {
					//if job hasn't read then add trigger to list lstTriggerWaitJob
					def mapTriggerWaitJob = [:]
					mapTriggerWaitJob["trigger"] = trigger_ori
					mapTriggerWaitJob["jobName"] = jobname
					jobDslInst.lstTriggerWaitJob.add(mapTriggerWaitJob)
				} else {
					jobDslInst.lstJobWaitJobClass.each {
						if (jobname == it["jobName"]) {
							def mapTriggerWaitAll = [:]
							mapTriggerWaitAll["trigger"] = trigger_ori
							mapTriggerWaitAll["jobName"] = jobname
							mapTriggerWaitAll["jobClass"] = it["jobClass"]
							jobDslInst.lstTriggerWaitAll.add(mapTriggerWaitAll)
						}
					}
				}
			}
		}
		listInstances.each {element_listinst ->
			def triggerInstances
			def isNotSchedule = true
			def job_inst = jobName + "_" + element_listinst.instancesName
			triggerInstances = jf.createTrigger(job_inst, 0, 10000)
			def mapTriggerWaitJob = [:]
			mapTriggerWaitJob["trigger"] = triggerInstances
			mapTriggerWaitJob["jobName"] = job_inst
			jobDslInst.lstTriggerWaitJob.add(mapTriggerWaitJob)
			jobDslInst.mapInstFileListInsts[instanceFile] = []
			jobDslInst.mapInstFileListInsts[instanceFile].add(job_inst)

			//Because job waiting for reading jobclass, add trigger, job, jobclass to list lstTriggerWaitAll
			jobDslInst.lstTriggerWaitJob.each {
				if (it["jobName"] == job_inst) {
					def mapTriggerWaitAll = [:]
					mapTriggerWaitAll["trigger"] = it["trigger"]
					mapTriggerWaitAll["jobName"] = job_inst
					mapTriggerWaitAll["jobClass"] = jobClassName
					jobDslInst.lstTriggerWaitAll.add(mapTriggerWaitAll)
				}
			}

			// Job instances detail
			def textParsed = ""
			jobFile.eachLine { line_job ->
				textParsed += line_job + "\n"
			}
			def tmpTextParesd = textParsed.trim()
			tmpTextParesd = tmpTextParesd.replace("testJob_07_1","testJob_07_1_inst_1")
			def clsJobInst = jobDslInst.loader.parseClass(tmpTextParesd, "testJob_07_1_inst_1")
			def scheduledJobInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJobInst, null, "testJob_07_1", "inst_1", jobDslInst.sender)
			jf.createJob(scheduledJobInst)
		}

		//		Data test:
		//		mapJobListInstances: [testJob_07_1:[[instancesName:inst_1, schedule:10i, params:null]]]
		//		mapJobInCls: [CLASS_A:[testJob_07_1, testJob_07_1_inst_1]]
		//		lstJobWaitJobClass: [[jobClass:CLASS_A, jobName:testJob_01_1], [jobClass:CLASS_A, jobName:testJob_01_1_inst_1]]
		//		lstTriggerWaitAll: [[trigger:Trigger 'DEFAULT.testJob_07_1_inst_1':  triggerClass: 'org.quartz.impl.triggers.SimpleTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null, jobName:testJob_07_1_inst_1, jobClass:CLASS_A]]
		//		lstTriggerWaitJob: [[trigger:Trigger 'DEFAULT.testJob_07_1':  triggerClass: 'org.quartz.impl.triggers.SimpleTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null, jobName:testJob_07_1], [trigger:Trigger 'DEFAULT.testJob_07_1_inst_1':  triggerClass: 'org.quartz.impl.triggers.SimpleTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null, jobName:testJob_07_1_inst_1]]
		//		jf.getJob: JobDetail 'DEFAULT.testJob_07_1_inst_1':  jobClass: 'org.wiperdog.jobmanager.internal.ObjectJob concurrentExectionDisallowed: true persistJobDataAfterExecution: false isDurable: true requestsRecovers: false

		// Remove Instances
		jobDslInst.removeInstances(instanceFile) 
		// Assert mapJobListInstances
		assertEquals(null, jobDslInst.mapJobListInstances['testJob_07_1'])
		// Assert mapJobInCls
		listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobInClsAfter = [:]
		mapJobInClsAfter[jobClassName] = listJobWaitCls
		assertEquals(mapJobInClsAfter, jobDslInst.mapJobInCls)
		// Assert lstJobWaitJobClass
		def lstJobWaitJobClassAfter = []
		mapJobWaitClass = [:]
		mapJobWaitClass['jobClass'] = jobClassName
		mapJobWaitClass['jobName'] = jobName
		lstJobWaitJobClassAfter.add(mapJobWaitClass)
		assertEquals(lstJobWaitJobClassAfter, jobDslInst.lstJobWaitJobClass)
		// Assert lstTriggerWaitAll
		def checklstTriggerWaitAll = "ok"
		lstTriggerWaitAll.each {
			if (it['jobName'] == "testJob_07_1_inst_1" && it['jobClass'] == "CLASS_A") {
				checklstTriggerWaitAll = "ng"
			}
		}
		assertEquals("ok", checklstTriggerWaitAll)
		// Assert lstTriggerWaitJob
		lstTriggerWaitJob.each {
			if (it['jobName'] == "testJob_07_1_inst_1") {
				checklstTriggerWaitJob = "ng"
			}
		}
		assertEquals("ok", checklstTriggerWaitJob)
		// Assert mapInstFileListInsts
		assertEquals(false, jobDslInst.mapInstFileListInsts[instanceFile].contains("testJob_07_1_inst_1"))
		// Assert job detail
		assertEquals(null, jf.getJob("testJob_07_1_inst_1"))
	}

	/**
	 * Instances running (exists: instances, job, trigger | not exists: jobclass)
	 * Instances has not define the schedule.
	 * Job has define job class
	 * 
	 * Expected:
	 * 		instances data in mapJobListInstances was removed
	 * 		instances data in mapJobInCls was removed
	 * 		instances data in lstJobWaitJobClass was removed
	 * 		instances data in lstTriggerWaitAll was removed
	 * 		instances data in lstTriggerWaitJob was removed
	 * 		instances data in mapInstFileListInsts was removed
	 * 		instances data in jobfacade was removed
	 */
	@Test
	public void removeInstances_07_2()throws Exception {
		File jobFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_07/testJob_07_2.job")
		File trgFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_07/testJob_07_2.trg")
		File instanceFile = new File(path + "/src/resources/jobdsl/removeInstance/test_Remove_07/testJob_07_2.instances")
		def listInstances = []
		shell = new GroovyShell()
		// Prepare data test
		jobDslInst.loader.clearCache()
		def clsJob = jobDslInst.loader.parseClass(jobFile)
		def scheduledJob = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, jobDslInst.sender)
		def jobName = scheduledJob.getJobName()
		def jobClassName = scheduledJob.getJobClassName()
		// mapJobListInstances, mapJobInCls, lstJobWaitJobClass
		def listJobWaitCls = []
		listJobWaitCls.add(jobName)
		def mapJobWaitClass = [:]
		mapJobWaitClass['jobClass'] = jobClassName
		mapJobWaitClass['jobName'] = jobName
		jobDslInst.lstJobWaitJobClass = []
		jobDslInst.lstJobWaitJobClass.add(mapJobWaitClass)
		def instEval = shell.evaluate(instanceFile)
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listJobWaitCls.add(jobName + "_" + it.key)
			def mapJobWaitClassInst = [:]
			mapJobWaitClassInst['jobClass'] = jobClassName
			mapJobWaitClassInst['jobName'] = jobName + "_" + it.key
			jobDslInst.lstJobWaitJobClass.add(mapJobWaitClassInst)
			listInstances.add(mapInstances)
			jobDslInst.mapJobListInstances[jobName] = listInstances
			jobDslInst.mapJobInCls = [:]
			jobDslInst.mapJobInCls[jobClassName] = listJobWaitCls

			trgFile.eachLine { aline ->
				def trg = shell.evaluate( "[" + aline + "]")
				def jobname = trg['job']
				def defaultSchedule = trg['schedule']
				// Create trigger and job original
				def trigger_ori = jf.createTrigger(jobName, 0, 60000)
				def job_ori = jf.getJob(jobname)
				def listInstNotSchedule = jobDslInst.mapJobListInstances[jobname]
				listInstNotSchedule.each {element_inst_not_schedule ->
					if(element_inst_not_schedule.schedule == null) {
						def triggerInstNotSchedule = jf.createTrigger(jobname + "_" + element_inst_not_schedule.instancesName, 0, 60000)
						def jobInstNotSchedule = jf.getJob(jobname + "_" + element_inst_not_schedule.instancesName)
						if(jobInstNotSchedule != null) {
							jobDslInst.lstJobWaitJobClass.each {
								if (jobname + "_" + element_inst_not_schedule.instancesName == it["jobName"]) {
									def mapTriggerWaitAll = [:]
									mapTriggerWaitAll["trigger"] = triggerInstNotSchedule
									mapTriggerWaitAll["jobName"] = jobname + "_" + element_inst_not_schedule.instancesName
									mapTriggerWaitAll["jobClass"] = it["jobClass"]
									jobDslInst.lstTriggerWaitAll.add(mapTriggerWaitAll)
								}
							}
						}
					}
				}
				if (trigger_ori != null && defaultSchedule != 'delete') {
					if (job_ori == null) {
						//if job hasn't read then add trigger to list lstTriggerWaitJob
						def mapTriggerWaitJob = [:]
						mapTriggerWaitJob["trigger"] = trigger_ori
						mapTriggerWaitJob["jobName"] = jobname
						jobDslInst.lstTriggerWaitJob.add(mapTriggerWaitJob)
					} else {
						jobDslInst.lstJobWaitJobClass.each {
							if (jobname == it["jobName"]) {
								def mapTriggerWaitAll = [:]
								mapTriggerWaitAll["trigger"] = trigger_ori
								mapTriggerWaitAll["jobName"] = jobname
								mapTriggerWaitAll["jobClass"] = it["jobClass"]
								jobDslInst.lstTriggerWaitAll.add(mapTriggerWaitAll)
							}
						}
					}
				}
			}
			listInstances.each {element_listinst ->
				def triggerInstances
				def isNotSchedule = true
				def job_inst = jobName + "_" + element_listinst.instancesName
				assertEquals(null, element_listinst.schedule)
				triggerInstances = jf.createTrigger(job_inst, 0, 60000)
				def mapTriggerWaitJob = [:]
				mapTriggerWaitJob["trigger"] = triggerInstances
				mapTriggerWaitJob["jobName"] = job_inst
				jobDslInst.lstTriggerWaitJob.add(mapTriggerWaitJob)
				jobDslInst.mapInstFileListInsts[instanceFile] = []
				jobDslInst.mapInstFileListInsts[instanceFile].add(job_inst)
				//Because job waiting for reading jobclass, add trigger, job, jobclass to list lstTriggerWaitAll
				jobDslInst.lstTriggerWaitJob.each {
					if (it["jobName"] == job_inst) {
						def mapTriggerWaitAll = [:]
						mapTriggerWaitAll["trigger"] = it["trigger"]
						mapTriggerWaitAll["jobName"] = job_inst
						mapTriggerWaitAll["jobClass"] = jobClassName
						jobDslInst.lstTriggerWaitAll.add(mapTriggerWaitAll)
					}
				}

				// Job instances detail
				def textParsed = ""
				jobFile.eachLine { line_job ->
					textParsed += line_job + "\n"
				}
				def tmpTextParesd = textParsed.trim()
				tmpTextParesd = tmpTextParesd.replace("testJob_07_2","testJob_07_2_inst_1")
				def clsJobInst = jobDslInst.loader.parseClass(tmpTextParesd, "testJob_07_2_inst_1")
				def scheduledJobInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJobInst, null, "testJob_07_2", "inst_1", jobDslInst.sender)
				jf.createJob(scheduledJobInst)
			}

			//Data test:
			//mapJobListInstances: [testJob_07_2:[[instancesName:inst_1, schedule:null, params:null]]]
			//mapJobInCls: [CLASS_A:[testJob_07_2, testJob_07_2_inst_1]]
			//lstJobWaitJobClass: [[jobClass:CLASS_A, jobName:testJob_07_2], [jobClass:CLASS_A, jobName:testJob_07_2_inst_1]]
			//lstTriggerWaitAll: [[trigger:null, jobName:testJob_07_2_inst_1, jobClass:CLASS_A]]
			//lstTriggerWaitJob: [[trigger:Trigger 'DEFAULT.testJob_07_2':  triggerClass: 'org.quartz.impl.triggers.SimpleTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null, jobName:testJob_07_2], [trigger:null, jobName:testJob_07_2_inst_1]]

			// Remove Instances
			jobDslInst.removeInstances(instanceFile)
			// Assert mapJobListInstances
			assertEquals(null, jobDslInst.mapJobListInstances['testJob_07_2'])
			// Assert mapJobInCls
			listJobWaitCls = []
			listJobWaitCls.add(jobName)
			def mapJobInClsAfter = [:]
			mapJobInClsAfter[jobClassName] = listJobWaitCls
			assertEquals(mapJobInClsAfter, jobDslInst.mapJobInCls)
			// Assert lstJobWaitJobClass
			def lstJobWaitJobClassAfter = []
			mapJobWaitClass = [:]
			mapJobWaitClass['jobClass'] = jobClassName
			mapJobWaitClass['jobName'] = jobName
			lstJobWaitJobClassAfter.add(mapJobWaitClass)
			assertEquals(lstJobWaitJobClassAfter, jobDslInst.lstJobWaitJobClass)
			// Assert lstTriggerWaitAll
			def checklstTriggerWaitAll = "ok"
			lstTriggerWaitAll.each {
				if (it['jobName'] == "testJob_07_2_inst_1" && it['jobClass'] == "CLASS_A") {
					checklstTriggerWaitAll = "ng"
				}
			}
			assertEquals("ok", checklstTriggerWaitAll)
			// Assert lstTriggerWaitJob
			lstTriggerWaitJob.each {
				if (it['jobName'] == "testJob_07_2_inst_1") {
					checklstTriggerWaitJob = "ng"
				}
			}
			assertEquals("ok", checklstTriggerWaitJob)
			// Assert mapInstFileListInsts
			assertEquals(false, jobDslInst.mapInstFileListInsts[instanceFile].contains("testJob_07_2_inst_1"))
			// Assert job detail
			assertEquals(null, jf.getJob("testJob_07_2_inst_1"))
		}
	}
}