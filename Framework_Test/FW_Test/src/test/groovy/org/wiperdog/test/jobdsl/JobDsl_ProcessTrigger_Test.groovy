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
public class JobDsl_ProcessTrigger_Test{
	public static final String PATH_TO_JOBCLASS     = "src/groovy/GroovyScheduledJob.groovy"
	public static final String PATH_TO_JOBDSLCLASS  = "src/groovy/JobDsl.groovy"
	String path = System.getProperty("user.dir")
	def jf
	Class jobExecutableCls
	Class jobDslCls
	def shell
	def binding
	def jobDslInst
	
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
		URL [] scriptpath123 = [new File(path + "/src/groovy").toURI().toURL()]
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
		  println "***** ERROR setup: " + e
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
	}
	
	/**
	 * Check output when /var/job only contains trigger file.
	 * Jobfile has not defined jobclass.
	 *
	 * Expected:
	 *		mapTrgFileListTrgs: add to map trigger file corresponding to jobname
	 *		defaultSchedule: get schedule corresponding to jobname
	 *		mapJobDefaultSchedule: add to map with schedule corresponding to jobname
	 *		lstTriggerWaitJob: add to list trigger wait job
	 */
	@Test
	public void processTrigger_01()throws Exception {
		// run test processTrigger
		boolean ret = jobDslInst.processTrigger(new File(path + "/src/resources/jobdsl/processTrigger/testJob_01.trg"))
		println "===============mapTrgFileListTrgs " + jobDslInst.mapTrgFileListTrgs
		println "===============defaultSchedule " + jobDslInst.defaultSchedule
		println "===============mapJobDefaultSchedule " + jobDslInst.mapJobDefaultSchedule
		println "===============lstTriggerWaitJob " + jobDslInst.lstTriggerWaitJob
		sleep(2000)
		def listJob = ["testJob_01"]
		LinkedHashMap tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_01.trg", listJob)
		// check map data contains trigger file name corresponding to job
		assertEquals(tmpMap, jobDslInst.mapTrgFileListTrgs)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_01", "10")
		// check map data contains schedule corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobDefaultSchedule)
		
		// check get schedule into trigger file
		assertTrue(jobDslInst.defaultSchedule == "10")
		
		// check trigger wait job
		assertEquals("testJob_01", jobDslInst.lstTriggerWaitJob[0].jobName)
		assertTrue(jobDslInst.lstTriggerWaitJob[0].trigger.toString().contains("DEFAULT.testJob_01"))
	}
	
	/**
	 * Check output when /var/job only contains trigger and job file.
	 * Jobfile has defined jobclass.
	 *
	 * Expected:
	 *		mapTrgFileListTrgs: add to map trigger file corresponding to jobname
	 *		defaultSchedule: get schedule corresponding to jobname
	 *		mapJobDefaultSchedule: add to map with schedule corresponding to jobname
	 *		lstJobWaitJobClass: add job wait jobclass
	 *		lstTriggerWaitJob: can not contains trigger wait job
	 *		lstTriggerWaitAll: add data of job to list trigger wait jobclass
	 */
	@Test
	public void processTrigger_02()throws Exception {
		// create job for test
		jobDslInst.processJob(new File(path + "/src/resources/jobdsl/processTrigger/testJob_02.job"))
		sleep(2000)
		
		// run test processTrigger
		boolean ret = jobDslInst.processTrigger(new File(path + "/src/resources/jobdsl/processTrigger/testJob_02.trg"))
		sleep(2000)
		def listJob = ["testJob_02"]
		LinkedHashMap tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_02.trg", listJob)
		// check map data contains trigger file name corresponding to job
		assertEquals(tmpMap, jobDslInst.mapTrgFileListTrgs)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("jobClass", "class_A")
		tmpMap.putAt("jobName", "testJob_02")
		// check job wait jobclass
		assertEquals(tmpMap, jobDslInst.lstJobWaitJobClass)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_02", "10")
		// check map data contains schedule corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobDefaultSchedule)
		
		// check get schedule into trigger file
		assertTrue(jobDslInst.defaultSchedule == "10")
		
		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check trigger wait jobclass
		assertEquals("testJob_02", jobDslInst.lstTriggerWaitAll[0].jobName)
		assertTrue(jobDslInst.lstTriggerWaitAll[0].trigger.toString().contains("DEFAULT.testJob_02"))
		assertEquals("class_A", jobDslInst.lstTriggerWaitAll[0].jobClass)
	}
	
	/**
	 * Check output when /var/job only contains trigger, job, class file.
	 * Jobfile has defined jobclass.
	 *
	 * Expected:
	 *		mapTrgFileListTrgs: add to map trigger file corresponding to jobname
	 *		defaultSchedule: get schedule corresponding to jobname
	 *		mapJobDefaultSchedule: add to map with schedule corresponding to jobname
	 *		lstJobWaitJobClass: can not job wait jobclass
	 *		lstTriggerWaitJob: can not contains trigger wait job
	 *		lstTriggerWaitAll: cannot contains trigger wait all
	 *		jobfacade: create schedule for job successfully, data will created to persistent and lastexecution file 
	 */
	@Test
	public void processTrigger_03()throws Exception {
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_03.txt")
		def PersistentData_Job = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_03.txt")
		def listPersistentDataFile = []
		def listLastExecutionFile = []
		// clear file before run test
		listPersistentDataFile = (new File(path + "/tmp/monitorjobdata/PersistentData/")).listFiles()
		listPersistentDataFile.each {
			it.delete()
		}
		// clear file before run test
		listLastExecutionFile = (new File(path + "/tmp/monitorjobdata/LastExecution/")).listFiles()
		listLastExecutionFile.each {
			it.delete()
		}
		
		// create job and class for test trigger
		jobDslInst.jobfacade.createJobClass("class_A")
		jobDslInst.processJob(new File(path + "/src/resources/jobdsl/processTrigger/testJob_03.job"))
		
		sleep(2000)
		// run test processTrigger
		boolean ret = jobDslInst.processTrigger(new File(path + "/src/resources/jobdsl/processTrigger/testJob_03.trg"))
		
		sleep(2000)
		def listJob = ["testJob_03"]
		LinkedHashMap tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_03.trg", listJob)
		// check map data contains trigger file name corresponding to job
		assertEquals(tmpMap, jobDslInst.mapTrgFileListTrgs)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_03", "10")
		// check map data contains schedule corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobDefaultSchedule)
		
		// check get schedule into trigger file
		assertTrue(jobDslInst.defaultSchedule == "10")
		
		// check size of list job wait jobclass is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait all is 0
		assertTrue(jobDslInst.lstTriggerWaitAll.size() == 0)
		
		// check job has created schedule success, data will write to persistentData and lastExecution file
		assertTrue(PersistentData_Job.exists() && !PersistentData_Job.getText().isEmpty())
		assertTrue(lastExecution_Job.exists() && !lastExecution_Job.getText().isEmpty())
	}
	
	/**
	 * Check output when /var/job only contains trigger, job, instances file.
	 * Jobfile has not define jobclass.
	 * Instances file has not define schedule.
	 *
	 * Expected:
	 *		mapTrgFileListTrgs: add to map trigger file corresponding to jobname
	 *		defaultSchedule: get schedule corresponding to jobname
	 *		mapJobDefaultSchedule: add to map with schedule corresponding to jobname
	 *		mapJobListInstances: contains list instances corresponding to job existing
	 *	 	lstJobWaitJobClass: can not job wait jobclass
	 *		lstTriggerWaitJob: can not contains trigger wait job
	 *		lstTriggerWaitAll: cannot contains trigger wait all
	 *		jobfacade: create schedule for job and instances successfully, data will created to persistent and lastexecution file 
	 */
	@Test
	public void processTrigger_04()throws Exception {
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_04.txt")
		def PersistentData_Job = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_04.txt")
		def PersistentData_Instance1 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_04_inst1.txt")
		def PersistentData_Instance2 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_04_inst2.txt")
		def lastExecution_Instance1 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_04_inst1.txt")
		def lastExecution_Instance2 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_04_inst2.txt")
		def listPersistentDataFile = []
		def listLastExecutionFile = []
		// clear file before run test
		listPersistentDataFile = (new File(path + "/tmp/monitorjobdata/PersistentData/")).listFiles()
		listPersistentDataFile.each {
			it.delete()
		}
		// clear file before run test
		listLastExecutionFile = (new File(path + "/tmp/monitorjobdata/LastExecution/")).listFiles()
		listLastExecutionFile.each {
			it.delete()
		}
		
		// create job for test trigger
		jobDslInst.processJob(new File(path + "/src/resources/jobdsl/processTrigger/testJob_04.job"))
		jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processTrigger/testJob_04.instances"))
		
		sleep(2000)
		// run test processTrigger
		boolean ret = jobDslInst.processTrigger(new File(path + "/src/resources/jobdsl/processTrigger/testJob_04.trg"))
		
		sleep(2000)
		def listJob = ["testJob_04"]
		LinkedHashMap tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_04.trg", listJob)
		// check map data contains trigger file name corresponding to job
		assertEquals(tmpMap, jobDslInst.mapTrgFileListTrgs)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_04", "10")
		// check map data contains schedule corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobDefaultSchedule)
		
		// check get default schedule for job and instances from trigger file
		assertTrue(jobDslInst.defaultSchedule == "10")
		
		// check size of list job wait jobclass is 0
		assertTrue(jobDslInst.lstJobWaitJobClass.size() == 0)
		
		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait all is 0
		assertTrue(jobDslInst.lstTriggerWaitAll.size() == 0)
		
		// check job has created schedule success, data will write to persistentData and lastExecution file 
		assertTrue(PersistentData_Job.exists() && !PersistentData_Job.getText().isEmpty())
		assertTrue(lastExecution_Job.exists() && !lastExecution_Job.getText().isEmpty())
		// check instances 1 has created schedule success, data will write to persistentData and lastExecution file
		assertTrue(PersistentData_Instance1.exists() && !PersistentData_Instance1.getText().isEmpty())
		assertTrue(lastExecution_Instance1.exists() && !lastExecution_Instance1.getText().isEmpty())
		// check instances 2 has created schedule success, data will write to persistentData and lastExecution file
		assertTrue(PersistentData_Instance2.exists() && !PersistentData_Instance2.getText().isEmpty())
		assertTrue(lastExecution_Instance2.exists() && !lastExecution_Instance2.getText().isEmpty())
	}
	
	/**
	 * Check output when /var/job only contains trigger, job, instances file.
	 * Jobfile has define jobclass.
	 * Does not exist class file corresponding to job.
	 *
	 * Expected:
	 *		mapTrgFileListTrgs: add to map trigger file corresponding to jobname
	 *		defaultSchedule: get schedule corresponding to jobname
	 *		mapJobDefaultSchedule: add to map with schedule corresponding to jobname.
	 *		mapJobListInstances: add list instances corresponding to jobname.
	 *		lstJobWaitJobClass: add job and instances to list job wait class
	 *		lstTriggerWaitJob: add data of trigger instances to list trigger wait job
	 *		lstTriggerWaitAll: add data of job and instances to list trigger wait jobclass
	 */
	@Test
	public void processTrigger_5()throws Exception {
		def tmpList = []
		LinkedHashMap tmpMap = new LinkedHashMap()
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_05.txt")
		def PersistentData_Job = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_05.txt")
		def PersistentData_Instance1 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_05_inst1.txt")
		def PersistentData_Instance2 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_05_inst2.txt")
		def lastExecution_Instance1 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_05_inst1.txt")
		def lastExecution_Instance2 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_05_inst2.txt")
		def listPersistentDataFile = []
		def listLastExecutionFile = []
		// clear file before run test
		listPersistentDataFile = (new File(path + "/tmp/monitorjobdata/PersistentData/")).listFiles()
		listPersistentDataFile.each {
			it.delete()
		}
		// clear file before run test
		listLastExecutionFile = (new File(path + "/tmp/monitorjobdata/LastExecution/")).listFiles()
		listLastExecutionFile.each {
			it.delete()
		}
		
		// create job for test trigger
		jobDslInst.processJob(new File(path + "/src/resources/jobdsl/processTrigger/testJob_05.job"))
		sleep(1000)
		// create instances for test trigger
		jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processTrigger/testJob_05.instances"))
		
		sleep(2000)
		// run test processTrigger
		boolean ret = jobDslInst.processTrigger(new File(path + "/src/resources/jobdsl/processTrigger/testJob_05.trg"))
		
		sleep(2000)
		def listJob = ["testJob_05"]
		tmpMap.putAt("testJob_05.trg", listJob)
		// check map data contains trigger file name corresponding to job
		assertEquals(tmpMap, jobDslInst.mapTrgFileListTrgs)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_05", "10i")
		// check map data contains schedule corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobDefaultSchedule)
		
		// check get default schedule for job from trigger file
		assertTrue(jobDslInst.defaultSchedule == "10i")

		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", "5")
		paramsMap.putAt("hostid", "params_inst01")
		tmpMap1.putAt("params", paramsMap)
		tmpList.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", "10")
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid", "params_inst02")
		tmpMap2.putAt("params", paramsMap)
		tmpList.add(tmpMap2)

		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_05", tmpList)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)
		
		tmpList = []
		tmpMap = new LinkedHashMap()
		tmpMap1 = new LinkedHashMap()
		tmpMap2 = new LinkedHashMap()
		tmpMap.putAt("jobClass", "class_A")
		tmpMap.putAt("jobName", "testJob_05")
		tmpList.add(tmpMap)
		tmpMap1.putAt("jobClass", "class_A")
		tmpMap1.putAt("jobName", "testJob_05_inst1")
		tmpList.add(tmpMap1)
		tmpMap2.putAt("jobClass", "class_A")
		tmpMap2.putAt("jobName", "testJob_05_inst2")
		tmpList.add(tmpMap2)
		// check list job and instances waiting class
		assertEquals(tmpList, jobDslInst.lstJobWaitJobClass)
		sleep(2000)
		
		// check trigger of instances wait job
		assertEquals("testJob_05_inst1", jobDslInst.lstTriggerWaitJob[0].jobName)
		assertTrue(jobDslInst.lstTriggerWaitJob[0].trigger.toString().contains("DEFAULT.testJob_05_inst1"))
		assertEquals("testJob_05_inst2", jobDslInst.lstTriggerWaitJob[1].jobName)
		assertTrue(jobDslInst.lstTriggerWaitJob[1].trigger.toString().contains("DEFAULT.testJob_05_inst2"))
		
		// check trigger of job and instances wait all
		assertEquals("testJob_05_inst1", jobDslInst.lstTriggerWaitAll[0].jobName)
		assertEquals("class_A", jobDslInst.lstTriggerWaitAll[0].jobClass)
		assertTrue(jobDslInst.lstTriggerWaitAll[0].trigger.toString().contains("DEFAULT.testJob_05_inst1"))
		assertEquals("testJob_05_inst2", jobDslInst.lstTriggerWaitAll[1].jobName)
		assertEquals("class_A", jobDslInst.lstTriggerWaitAll[1].jobClass)
		assertTrue(jobDslInst.lstTriggerWaitAll[1].trigger.toString().contains("DEFAULT.testJob_05_inst2"))
		assertEquals("testJob_05", jobDslInst.lstTriggerWaitAll[2].jobName)
		assertEquals("class_A", jobDslInst.lstTriggerWaitAll[2].jobClass)
		assertTrue(jobDslInst.lstTriggerWaitAll[2].trigger.toString().contains("DEFAULT.testJob_05"))
		
		// check job and instances can not created process success
		assertTrue(!PersistentData_Job.exists())
		assertTrue(!lastExecution_Job.exists())
		
		assertTrue(!PersistentData_Instance1.exists())
		assertTrue(!lastExecution_Instance1.exists())
		
		assertTrue(!PersistentData_Instance2.exists())
		assertTrue(!lastExecution_Instance2.exists())
	}
	
	/**
	 * Check output when /var/job only contains trigger, job, instances, class file.
	 * Jobfile has define jobclass.
	 * Exist class file corresponding to job.
	 *
	 * Expected:
	 *		mapTrgFileListTrgs: add to map trigger file corresponding to jobname
	 *		defaultSchedule: get schedule corresponding to jobname
	 *		mapJobDefaultSchedule: add to map with schedule corresponding to jobname
	 *	 	lstJobWaitJobClass: cannot contains job or instances wait class
	 *	 	lstTriggerWaitJob: cannot contains trigger wait job
	 *		lstTriggerWaitAll: cannot contains trigger wait all
	 *		jobfacade: create schedule for job and instances successfully, data will created to persistent and lastexecution file 
	 */
	@Test
	public void processTrigger_6()throws Exception {
		def tmpList = []
		LinkedHashMap tmpMap = new LinkedHashMap()
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_06.txt")
		def PersistentData_Job = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_06.txt")
		def PersistentData_Instance1 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_06_inst1.txt")
		def PersistentData_Instance2 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_06_inst2.txt")
		def lastExecution_Instance1 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_06_inst1.txt")
		def lastExecution_Instance2 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_06_inst2.txt")
		def listPersistentDataFile = []
		def listLastExecutionFile = []
		// clear file before run test
		listPersistentDataFile = (new File(path + "/tmp/monitorjobdata/PersistentData/")).listFiles()
		listPersistentDataFile.each {
			it.delete()
		}
		// clear file before run test
		listLastExecutionFile = (new File(path + "/tmp/monitorjobdata/LastExecution/")).listFiles()
		listLastExecutionFile.each {
			it.delete()
		}
		
		// create class for test
		jobDslInst.jobfacade.createJobClass("class_A")
		// create job for test trigger
		jobDslInst.processJob(new File(path + "/src/resources/jobdsl/processTrigger/testJob_06.job"))
		sleep(1000)
		// create instances for test trigger
		jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processTrigger/testJob_06.instances"))
		sleep(2000)
		// run test processTrigger
		boolean ret = jobDslInst.processTrigger(new File(path + "/src/resources/jobdsl/processTrigger/testJob_06.trg"))
		
		sleep(2000)
		def listJob = ["testJob_06"]
		tmpMap.putAt("testJob_06.trg", listJob)
		// check map data contains trigger file name corresponding to job
		assertEquals(tmpMap, jobDslInst.mapTrgFileListTrgs)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_06", "20")
		// check map data contains schedule corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobDefaultSchedule)
		
		// check get default schedule for job from trigger file
		assertTrue(jobDslInst.defaultSchedule == "20")

		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", "5")
		paramsMap.putAt("hostid", "params_inst01")
		tmpMap1.putAt("params", paramsMap)
		tmpList.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", "10")
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid", "params_inst02")
		tmpMap2.putAt("params", paramsMap)
		tmpList.add(tmpMap2)

		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_06", tmpList)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)
		
		// check size of list job wait jobclass is 0
		assertTrue(jobDslInst.lstJobWaitJobClass.size() == 0)
		
		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait all is 0
		assertTrue(jobDslInst.lstTriggerWaitAll.size() == 0)
		
		// check job has created schedule success, data will write to persistentData and lastExecution file 
		assertTrue(PersistentData_Job.exists() && !PersistentData_Job.getText().isEmpty())
		assertTrue(lastExecution_Job.exists() && !lastExecution_Job.getText().isEmpty())
		// check instances 1 has created schedule success, data will write to persistentData and lastExecution file
		assertTrue(PersistentData_Instance1.exists() && !PersistentData_Instance1.getText().isEmpty())
		assertTrue(lastExecution_Instance1.exists() && !lastExecution_Instance1.getText().isEmpty())
		// check instances 2 has created schedule success, data will write to persistentData and lastExecution file
		assertTrue(PersistentData_Instance2.exists() && !PersistentData_Instance2.getText().isEmpty())
		assertTrue(lastExecution_Instance2.exists() && !lastExecution_Instance2.getText().isEmpty())
	}
}






















