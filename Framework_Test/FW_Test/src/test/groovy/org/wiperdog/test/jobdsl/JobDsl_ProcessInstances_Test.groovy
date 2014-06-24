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
public class JobDsl_ProcessInstances_Test{
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
	}
	
	/**
	 * Check output when /var/job only contains instances file.
	 *
	 * Expected: 
	 *		mapJobListInstances: add to map instances corresponding to job
	 *		mapInstancesWaitJob: add to map instances file wait job
	 *		mapInstFileListInsts: add all instance of job corresponding to instances file
	 */
	@Test
	public void processInstances_01()throws Exception {
		def listInstance = []
		LinkedHashMap tmpMap = new LinkedHashMap()
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		File instanceFile = new File(path + "/src/resources/jobdsl/processInstance/testJob_01.instances")
		
		// run test processInstances
		boolean ret = jobDslInst.processInstances(instanceFile)
				
		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", "10i")
		paramsMap.putAt("hostid","params_inst01")
		tmpMap1.putAt("params", paramsMap)
		listInstance.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", "5i")
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid","params_inst02")
		tmpMap2.putAt("params", paramsMap)
		listInstance.add(tmpMap2)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_01",listInstance)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)
		
		listInstance = []
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_01.instances",listInstance)
		// check instances of job corresponding to instances file
		assertEquals(tmpMap, jobDslInst.mapInstFileListInsts)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_01",instanceFile)
		// check add to map instances file wait job
		assertEquals(tmpMap, jobDslInst.mapInstancesWaitJob)
	}
	
	/**
	 * Check output when /var/job only contains instances and job file.
	 * Instances has not define schedule corresponding.
	 * jobclass has not defined.
	 *
	 * Expected:
	 *		mapJobListInstances: add to map instances corresponding to job
	 *		mapInstancesWaitJob: can not contains instances wait job
	 *		mapInstFileListInsts: add all instance of job corresponding to instances file
	 *		jobfacade: can not create schedule of job because the trigger file not exists
	 *		jobfacade: create schedule for instances with "default schedule"
	 * 		lstTriggerWaitJob: can not contains trigger wait instances
	 *		lstJobWaitJobClass: can not contains job wait jobclass
	 */
	@Test
	public void processInstances_02()throws Exception {
		def pathToJob = new File(path + "/src/resources/jobdsl/processInstance/testJob_02.job")
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_02.txt")
		def PersistentData_Instance1 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_02_inst1.txt")
		def PersistentData_Instance2 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_02_inst2.txt")
		def lastExecution_Instance1 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_02_inst1.txt")
		def lastExecution_Instance2 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_02_inst2.txt")
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
		sleep(2000)
		
		def listInstance = []
		LinkedHashMap tmpMap = new LinkedHashMap()
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		
		// create map job corresponding to jobfile
		tmpMap.putAt("testJob_02", pathToJob)
		jobDslInst.mapJobJobFile = tmpMap
		
		// create default of schedule
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_02", "20i")
		jobDslInst.mapJobDefaultSchedule = tmpMap
		
		// run test processInstances
		boolean ret = jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processInstance/testJob_02.instances"))
		println "===============mapInstancesWaitJob " + jobDslInst.mapInstancesWaitJob
		println "===============mapInstFileListInsts " + jobDslInst.mapInstFileListInsts
		println "===============mapJobListInstances " + jobDslInst.mapJobListInstances
		println "===============mapJobInCls " + jobDslInst.mapJobInCls
		println "===============lstJobWaitJobClass " + jobDslInst.lstJobWaitJobClass
		println "===============mapJobDefaultSchedule " + jobDslInst.mapJobDefaultSchedule
		println "===============lstTriggerWaitJob " + jobDslInst.lstTriggerWaitJob
		println "===============lstTriggerWaitAll " + jobDslInst.lstTriggerWaitAll
		// check can not contains instances wait job
		assertTrue(jobDslInst.mapInstancesWaitJob.size() == 0)
				
		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", null)
		paramsMap.putAt("hostid","params_inst01")
		tmpMap1.putAt("params", paramsMap)
		listInstance.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", null)
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid","params_inst02")
		tmpMap2.putAt("params", paramsMap)
		listInstance.add(tmpMap2)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_02",listInstance)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)

		listInstance = ["testJob_02_inst1", "testJob_02_inst2"]
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_02.instances",listInstance)
		// check instances of job corresponding to instances file
		assertEquals(tmpMap, jobDslInst.mapInstFileListInsts)

		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait all is 0
		assertTrue(jobDslInst.lstTriggerWaitAll.size() == 0)
		sleep(2000)
		// check instances of job create schedule success
		assertTrue(PersistentData_Instance1.exists() && !PersistentData_Instance1.getText().isEmpty())	
		assertTrue(PersistentData_Instance2.exists() && !PersistentData_Instance2.getText().isEmpty())
		assertTrue(lastExecution_Instance1.exists() && !lastExecution_Instance1.getText().isEmpty())
		assertTrue(lastExecution_Instance2.exists() && !lastExecution_Instance2.getText().isEmpty())
		// check job can not create schedule
		assertTrue(!lastExecution_Job.exists())
	}
	
	/**
	 * Check output when /var/job only contains instances and job file.
	 * Instances file contains 2 instance of job and only instances_1 has defined schedule corresponding.
	 * jobclass has not defined.
	 *
	 * Expected:
	 *		mapJobListInstances: add to map instances corresponding to job
	 *		mapInstancesWaitJob: can not contains instances wait job
	 *		mapInstFileListInsts: add all instance of job corresponding to instances file
	 *		jobfacade: can not create schedule of job because the trigger file not exists
	 *		jobfacade: create schedule for instances (only instance_1 has created schedule)
	 * 		lstTriggerWaitJob: can not contains trigger wait instances
	 *		lstJobWaitJobClass: can not contains job wait jobclass
	 */
	@Test
	public void processInstances_03()throws Exception {
		def pathToJob = new File(path + "/src/resources/jobdsl/processInstance/testJob_03.job")
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_03.txt")
		def PersistentData_Instance1 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_03_inst1.txt")
		def PersistentData_Instance2 = new File(path + "/tmp/monitorjobdata/PersistentData/testJob_03_inst2.txt")
		def lastExecution_Instance1 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_03_inst1.txt")
		def lastExecution_Instance2 = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_03_inst2.txt")
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
		sleep(2000)
		
		def listInstance = []
		LinkedHashMap tmpMap = new LinkedHashMap()
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		
		// create map job corresponding to jobfile
		tmpMap.putAt("testJob_03", pathToJob)
		jobDslInst.mapJobJobFile = tmpMap
		
		// run test processInstances
		boolean ret = jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processInstance/testJob_03.instances"))
		println "===============mapInstancesWaitJob " + jobDslInst.mapInstancesWaitJob
		println "===============mapInstFileListInsts " + jobDslInst.mapInstFileListInsts
		println "===============mapJobListInstances " + jobDslInst.mapJobListInstances
		println "===============mapJobInCls " + jobDslInst.mapJobInCls
		println "===============lstJobWaitJobClass " + jobDslInst.lstJobWaitJobClass
		println "===============mapJobDefaultSchedule " + jobDslInst.mapJobDefaultSchedule
		println "===============lstTriggerWaitJob " + jobDslInst.lstTriggerWaitJob
		println "===============lstTriggerWaitAll " + jobDslInst.lstTriggerWaitAll
		// check can not contains instances wait job
		assertTrue(jobDslInst.mapInstancesWaitJob.size() == 0)
				
		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", "10i")
		paramsMap.putAt("hostid","params_inst01")
		tmpMap1.putAt("params", paramsMap)
		listInstance.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", null)
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid","params_inst02")
		tmpMap2.putAt("params", paramsMap)
		listInstance.add(tmpMap2)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_03",listInstance)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)
		
		listInstance = ["testJob_03_inst1", "testJob_03_inst2"]
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_03.instances",listInstance)
		// check instances of job corresponding to instances file
		assertEquals(tmpMap, jobDslInst.mapInstFileListInsts)
		
		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait all is 0
		assertTrue(jobDslInst.lstTriggerWaitAll.size() == 0)
		
		// check size of map default of job is 0
		assertTrue(jobDslInst.mapJobDefaultSchedule.size() == 0)
		
		sleep(2000)
		// check instances_1 of job has created schedule success
		assertTrue(PersistentData_Instance1.exists() && !PersistentData_Instance1.getText().isEmpty())
		assertTrue(lastExecution_Instance1.exists() && !lastExecution_Instance1.getText().isEmpty())
		
		// check instances_2 can not create schedule
		assertTrue(!PersistentData_Instance2.exists())
		assertTrue(!lastExecution_Instance2.exists())
		
		// check job can not create schedule
		assertTrue(!lastExecution_Job.exists())
	}
	
	/**
	 * Check output when /var/job only contains instances and job file.
	 * Jobfile has defined jobclass.
	 * jobclass file does not exist.
	 * trigger of instances is not null.
	 *
	 * Expected:
	 *		mapJobListInstances: add to map instances corresponding to job
	 *		mapInstancesWaitJob: can not contains instances wait job
	 *		mapInstFileListInsts: add all instance of job corresponding to instances file
	 *		jobfacade: create job with schedule of job
	 *		mapJobInCls: add instances corresponding to jobclass
	 *		lstJobWaitJobClass: add job to list wait jobclass
	 *		lstTriggerWaitJob: add data of job instances to list trigger wait job
	 * 		lstTriggerWaitAll: add data of job instances to list trigger wait all
	 */
	//@Test check again
	public void processInstances_04()throws Exception {
		def pathToJob = new File(path + "/src/resources/jobdsl/processInstance/testJob_04.job")
		LinkedHashMap tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_04", pathToJob)
		jobDslInst.mapJobJobFile = tmpMap		
		
		// run test processInstances
		boolean ret = jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processInstance/testJob_04.instances"))
		// check can not contains instances wait job
		assertTrue(jobDslInst.mapInstancesWaitJob.size() == 0)
		
		def listInstance = []
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		
		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", "10")
		paramsMap.putAt("hostid","params_inst01")
		tmpMap1.putAt("params", paramsMap)
		listInstance.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", "5i")
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid","params_inst02")
		tmpMap2.putAt("params", paramsMap)
		listInstance.add(tmpMap2)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_04",listInstance)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)
		
		listInstance = ["testJob_04_inst1", "testJob_04_inst2"]
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_04.instances",listInstance)
		// check instances of job corresponding to instances file
		assertEquals(tmpMap, jobDslInst.mapInstFileListInsts)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("class_A",listInstance)
		// check instances corresponding to jobclass
		assertEquals(tmpMap, jobDslInst.mapJobInCls)
		
		listInstance = []
		tmpMap1 = new LinkedHashMap()
		tmpMap1.putAt("jobClass", "class_A")
		tmpMap1.putAt("jobName", "testJob_04_inst1")
		listInstance.add(tmpMap1)
		tmpMap2 = new LinkedHashMap()
		tmpMap2.putAt("jobClass", "class_A")
		tmpMap2.putAt("jobName", "testJob_04_inst2")
		listInstance.add(tmpMap2)
		// check list wait jobclass
		assertEquals(listInstance, jobDslInst.lstJobWaitJobClass)

		// check trigger of instances wait job
		assertEquals("testJob_04_inst1", jobDslInst.lstTriggerWaitJob[0].jobName)
		assertTrue(jobDslInst.lstTriggerWaitJob[0].trigger.toString().contains("DEFAULT.testJob_04_inst1"))
		assertEquals("testJob_04_inst2", jobDslInst.lstTriggerWaitJob[1].jobName)
		assertTrue(jobDslInst.lstTriggerWaitJob[1].trigger.toString().contains("DEFAULT.testJob_04_inst2"))
		
		// check trigger of instances wait all
		assertEquals("testJob_04_inst1", jobDslInst.lstTriggerWaitAll[0].jobName)
		assertEquals("class_A", jobDslInst.lstTriggerWaitAll[0].jobClass)
		assertTrue(jobDslInst.lstTriggerWaitAll[0].trigger.toString().contains("DEFAULT.testJob_04_inst1"))
		assertEquals("testJob_04_inst2", jobDslInst.lstTriggerWaitAll[1].jobName)
		assertEquals("class_A", jobDslInst.lstTriggerWaitAll[1].jobClass)
		assertTrue(jobDslInst.lstTriggerWaitAll[1].trigger.toString().contains("DEFAULT.testJob_04_inst2"))
	}
	
	/**
	 * Check output when /var/job only contains instances, job, class file.
	 * Instances has not define schedule corresponding.
	 * Jobfile has defined jobclass.
	 *
	 * Expected:
	 *		mapJobListInstances: add to map instances corresponding to job
	 *		mapInstancesWaitJob: can not contains instances wait job
	 *		mapInstFileListInsts: add all instance of job corresponding to instances file
	 *		jobfacade: can not create schedule of job because the trigger file not exists
	 *		mapJobInCls: add instances corresponding to jobclass
	 *		jobfacade: create schedule for instances with "default schedule"
	 * 		lstJobWaitJobClass: can not contains job wait jobclass
	 * 		lstTriggerWaitJob: can not contains trigger wait instances
	 * 		lstTriggerWaitAll: can not contains trigger wait all
	 */
	@Test
	public void processInstances_05()throws Exception {
		def pathToJob = new File(path + "/src/resources/jobdsl/processInstance/testJob_05.job")
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_05.txt")
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
		sleep(2000)
		
		def listInstance = []
		LinkedHashMap tmpMap = new LinkedHashMap()
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		
		// create map job corresponding to jobfile
		tmpMap.putAt("testJob_05", pathToJob)
		jobDslInst.mapJobJobFile = tmpMap
		
		// create map default of schedule
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_05", "10")
		jobDslInst.mapJobDefaultSchedule = tmpMap
		
		// create jobclass for instances and job
		jobDslInst.jobfacade.createJobClass("class_A")
		
		// run test processInstances
		boolean ret = jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processInstance/testJob_05.instances"))
		
		// check can not contains instances wait job
		assertTrue(jobDslInst.mapInstancesWaitJob.size() == 0)

		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", null)
		paramsMap.putAt("hostid","params_inst01")
		tmpMap1.putAt("params", paramsMap)
		listInstance.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", null)
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid","params_inst02")
		tmpMap2.putAt("params", paramsMap)
		listInstance.add(tmpMap2)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_05",listInstance)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)

		listInstance = ["testJob_05_inst1", "testJob_05_inst2"]
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_05.instances",listInstance)
		// check instances of job corresponding to instances file
		assertEquals(tmpMap, jobDslInst.mapInstFileListInsts)

		tmpMap = new LinkedHashMap()
		tmpMap.putAt("class_A",listInstance)
		// check instances of job corresponding to jobclass
		assertEquals(tmpMap, jobDslInst.mapJobInCls)
		
		// check size of list job wait class is 0
		assertTrue(jobDslInst.lstJobWaitJobClass.size() == 0)

		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait all is 0
		assertTrue(jobDslInst.lstTriggerWaitAll.size() == 0)
		sleep(2000)
		// check instances of job create schedule success
		assertTrue(PersistentData_Instance1.exists() && !PersistentData_Instance1.getText().isEmpty())
		assertTrue(PersistentData_Instance2.exists() && !PersistentData_Instance2.getText().isEmpty())
		assertTrue(lastExecution_Instance1.exists() && !lastExecution_Instance1.getText().isEmpty())
		assertTrue(lastExecution_Instance2.exists() && !lastExecution_Instance2.getText().isEmpty())
		// check job can not create schedule
		assertTrue(!lastExecution_Job.exists())
	}
	
	/**
	 * Check output when /var/job only contains instances, job, class file.
	 * Instances file contains 2 instance of job and only instances_2 has defined schedule corresponding.
	 * Jobfile has defined jobclass.
	 *
	 * Expected:
	 *		mapJobListInstances: add to map instances corresponding to job
	 *		mapInstancesWaitJob: can not contains instances wait job
	 *		mapInstFileListInsts: add all instance of job corresponding to instances file
	 *		jobfacade: can not create schedule of job because the trigger file not exists
	 *		mapJobInCls: add instances corresponding to jobclass
	 *		jobfacade: create schedule for instances corresponding to schedule defined (only instance_2 has created schedule)
	 * 		lstJobWaitJobClass: can not contains job wait jobclass
	 * 		lstTriggerWaitJob: can not contains trigger wait instances
	 * 		lstTriggerWaitAll: can not contains trigger wait all
	 */
	@Test
	public void processInstances_06()throws Exception {
		def pathToJob = new File(path + "/src/resources/jobdsl/processInstance/testJob_06.job")
		def lastExecution_Job = new File(path + "/tmp/monitorjobdata/LastExecution/testJob_06.txt")
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
		sleep(2000)
		
		def listInstance = []
		LinkedHashMap tmpMap = new LinkedHashMap()
		LinkedHashMap tmpMap1 = new LinkedHashMap()
		LinkedHashMap tmpMap2 = new LinkedHashMap()
		LinkedHashMap paramsMap = new LinkedHashMap()
		
		// create map job corresponding to jobfile
		tmpMap.putAt("testJob_06", pathToJob)
		jobDslInst.mapJobJobFile = tmpMap
		
		// create jobclass for instances and job
		jobDslInst.jobfacade.createJobClass("class_A")
		
		// run test processInstances
		boolean ret = jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/processInstance/testJob_06.instances"))
		
		// check can not contains instances wait job
		assertTrue(jobDslInst.mapInstancesWaitJob.size() == 0)

		tmpMap1.putAt("instancesName", "inst1")
		tmpMap1.putAt("schedule", null)
		paramsMap.putAt("hostid","params_inst01")
		tmpMap1.putAt("params", paramsMap)
		listInstance.add(tmpMap1)
				
		tmpMap2.putAt("instancesName", "inst2")
		tmpMap2.putAt("schedule", "10")
		paramsMap = new LinkedHashMap()
		paramsMap.putAt("hostid","params_inst02")
		tmpMap2.putAt("params", paramsMap)
		listInstance.add(tmpMap2)
		
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_06",listInstance)
		// check map instances corresponding to job
		assertEquals(tmpMap, jobDslInst.mapJobListInstances)

		listInstance = ["testJob_06_inst1", "testJob_06_inst2"]
		tmpMap = new LinkedHashMap()
		tmpMap.putAt("testJob_06.instances",listInstance)
		// check instances of job corresponding to instances file
		assertEquals(tmpMap, jobDslInst.mapInstFileListInsts)

		tmpMap = new LinkedHashMap()
		tmpMap.putAt("class_A",listInstance)
		// check instances of job corresponding to jobclass
		assertEquals(tmpMap, jobDslInst.mapJobInCls)
		
		// check map default of schedule is empty
		assertTrue(jobDslInst.mapJobDefaultSchedule.size() == 0)
		
		// check size of list job wait class is 0
		assertTrue(jobDslInst.lstJobWaitJobClass.size() == 0)

		// check size of list trigger wait job is 0
		assertTrue(jobDslInst.lstTriggerWaitJob.size() == 0)
		
		// check size of list trigger wait all is 0
		assertTrue(jobDslInst.lstTriggerWaitAll.size() == 0)
		
		sleep(2000)
		// check instances_2 of job has created schedule success
		assertTrue(PersistentData_Instance2.exists() && !PersistentData_Instance2.getText().isEmpty())
		assertTrue(lastExecution_Instance2.exists() && !lastExecution_Instance2.getText().isEmpty())
		
		// check instances_1 of job can not create schedule
		assertTrue(!PersistentData_Instance1.exists())
		assertTrue(!lastExecution_Instance1.exists())
		
		// check job can not create schedule
		assertTrue(!lastExecution_Job.exists())
	}
}