package org.wiperdog.test.jobdsl

import static org.junit.Assert.*
import static org.ops4j.pax.exam.CoreOptions.*

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
public class GroovyScheduledJobTest{
	public static final String PATH_TO_JOBCLASS     = "src/groovy/GroovyScheduledJob.groovy"
	
	String path = System.getProperty("user.dir")
	def jf
	Class jobExecutableCls
	def jobExecutableInst
	def shell
	def binding
	ClassLoaderUtil clzzLoaderUtl = new ClassLoaderUtil();	
	
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
		mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.2.8").startLevel(3),
		mavenBundle("org.wiperdog", "org.wiperdog.rshell.api", "0.1.0").startLevel(3),
		mavenBundle("org.quartz-scheduler", "quartz", "2.2.1").startLevel(3),
		mavenBundle("org.wiperdog", "org.wiperdog.jobmanager", "0.2.3-SNAPSHOT").startLevel(3),
		junitBundles()
		);
	}
	
	@Before
	public void setup() throws Exception {		
		jf = context.getService(context.getServiceReference("org.wiperdog.jobmanager.JobFacade"));
		URL [] scriptpath123 = [new File(path + "/src/groovy").toURI().toURL()]
		// Load class using the inherit class loader from parent class loader		
		clzzLoaderUtl.addURL(scriptpath123);		
		println "***** Start loading reference groovy classes"
		try{			
			//-- Setting Groovy shell
			binding = new Binding()
			binding.setVariable("felix_home", path)
			//-- Merging class loader 
			RootLoader rootloader = new RootLoader(scriptpath123, clzzLoaderUtl.getClzzLoader())
			shell = new GroovyShell(rootloader,binding)
			jobExecutableCls = shell.getClassLoader().loadClass("GroovyScheduledJob")
			
		}catch(Exception e){
		  println "***** "+e
		}	
		println "***** Complete setup phase!"
	}
	
	@After
	public void shutdown() throws Exception {
		shell = null
		binding = null
		jobExecutableCls = null
		jobExecutableInst = null
	}
	
	/**
	 * Get job instance from job file and related setting such as instances, params 
	 * @throws Exception
	 */
	//@Test
	public void getJObInstance_normal() throws Exception {
		shell.getClassLoader().clearCache()
		def senderClzz = shell.getClassLoader().loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		def jobFile = new File(path + "/src/resources/scheduledjob/job.job")
		def clsJob = shell.getClassLoader().parseClass(jobFile)
		 
		jobExecutableInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, sender)
		def objectJob = jobExecutableInst.getJobInstance()
		def jobName = jobExecutableInst.getJobName()
		assertNotNull(objectJob)
		assertEquals("test", jobName)		
	}
	//@Test
	public void getJObInstance_instances() throws Exception {
		def loader = shell.getClassLoader()
		loader.clearCache()
		def senderClzz = shell.getClassLoader().loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		def jobFile = new File(path + "/src/resources/scheduledjob/job_normal.job")
		
		//- Process instance file
		def instfile = new File(path + "/src/resources/scheduledjob/job_normal.instances")
		def listInstances = []
		// Evaluate file .instances
		def instEval = shell.evaluate(instfile)
		def jobName = instfile.getName().substring(0, instfile.getName().indexOf(".instances"))
		def textParsed = ""
		
		// Process to get list instances of job
		instEval.each {
			def mapInstances = [:]
			def instancesName
			def schedule
			def parmas
			mapInstances['instancesName'] = it.key
			mapInstances['schedule'] = it.value.schedule
			mapInstances['params'] = it.value.params
			listInstances.add(mapInstances)
		}		 
		// If .job file is loaded then create text from .job file to parse class later
		// Else add .instances file to map waiting job and return to stop notify .instances file
		
		jobFile.eachLine { line_job ->
			textParsed += line_job + "\n"
		}
		
		int i =0
		// create instances of jobs
		listInstances.each {element_listinst ->
			def triggerInstances
			def isNotSchedule = true
			def job_inst = jobName + "_" + element_listinst.instancesName
			
			def tmpTextParesd = textParsed.trim()
			// tmpTextParesd != null means .job file is loaded
			if(tmpTextParesd != null) {
				// Parse class and create job instances
				if(tmpTextParesd.contains(jobName)) {
					tmpTextParesd = tmpTextParesd.replace(jobName,job_inst)
				}
				def clsJob = loader.parseClass(tmpTextParesd, job_inst)
				jobExecutableInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, element_listinst.params, jobName, element_listinst.instancesName, sender)
				// assertion
				def objectJob = jobExecutableInst.getJobInstance()
				def testJobName = jobExecutableInst.getJobName()
				
				assertNotNull(objectJob)				
				if(i==0)
					assertEquals("job_normal_inst1", testJobName)
				else
					assertEquals("job_normal_inst2", testJobName)
			}
			i++
		}
	}
	
	//@Test
	public void getJobClassName() throws Exception {	
		shell.getClassLoader().clearCache()
		def senderClzz = shell.getClassLoader().loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		def jobFile = new File(path + "/src/resources/scheduledjob/job_with_jobclass.job")
		def clsJob = shell.getClassLoader().parseClass(jobFile)
		 
		jobExecutableInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, sender)
		def jobClassName = jobExecutableInst.getJobClassName()
		assertEquals("CLASS_A", jobClassName)
	}
	
	//@Test
	public void execute() throws Exception {		
		
		def loader = shell.getClassLoader()
		loader.clearCache()
		
		def jobFile = new File(path + "/src/resources/scheduledjob/job_normal.job")
		def clsJob = shell.getClassLoader().parseClass(jobFile)
		
		def senderClzz = loader.loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		 
		jobExecutableInst = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, sender)		
		def jobName = jobExecutableInst.getJobName()
		def wpdJobDataMapClzz = shell.getClassLoader().loadClass("WpdJobDataMap")
		def wpdJobDataMap = wpdJobDataMapClzz.newInstance()
		def rv = jobExecutableInst.execute(wpdJobDataMap)		
		def isJobFinishedSuccessfully = jobExecutableInst.isJobFinishedSuccessfully
		assertEquals(true, isJobFinishedSuccessfully)
		assertNotEquals(rv.indexOf('RESULT'), -1)
	}
	
	//@Test
	public void getJobExecutedStatus() throws Exception {	
		def loader = shell.getClassLoader()
		loader.clearCache()
		
		def senderClzz = loader.loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		def jobFile = new File(path + "/src/resources/scheduledjob/job_sleep.job")
		def clsJob = shell.getClassLoader().parseClass(jobFile)
		def jobInstance = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, sender)
		jobInstance.isJobFinishedSuccessfully = false
		def rv = null
		// Define new Thread to execute the job, because the job will be slept for 3 second while
		// this main thread still running independently
		def t = new Thread(new Runnable(){
			void run() {
				def wpdJobDataMapClzz = shell.getClassLoader().loadClass("WpdJobDataMap")
				def wpdJobDataMap = wpdJobDataMapClzz.newInstance()
				rv = jobInstance.execute(wpdJobDataMap)
			}
		})
		t.start()
		// At this time Job still not finished in above thread (sleep 3 second), expected executed status should be false
		def isJobFinishedSuccessfully = jobInstance.isJobFinishedSuccessfully
		assertEquals(false, isJobFinishedSuccessfully)
		
		//Sleep main thread for 6 seconds to wait the job to be done
		Thread.sleep(6000)
		// re-assign executed status, should be true by now
		isJobFinishedSuccessfully = jobInstance.isJobFinishedSuccessfully		
		assertEquals(true, isJobFinishedSuccessfully)		
		// Finally assert the return value (String of 'RESULT')
		assertNotEquals(rv.indexOf('RESULT'), -1)
	}

	//@Test
	public void stop() throws Exception {
		def loader = shell.getClassLoader()
		loader.clearCache()
		def senderClzz = loader.loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		def jobFile = new File(path + "/src/resources/scheduledjob/job_sleep.job")
		def clsJob = shell.getClassLoader().parseClass(jobFile)
		 
		def jobInstance = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, sender)
		jobInstance.isJobFinishedSuccessfully = false
		
		def rv = null
		// Define new Thread to execute the job, because the job will be slept for 3 second while
		// this main thread still running independently
		def t = new Thread(new Runnable(){
			void run() {
				try{
					def wpdJobDataMapClzz = shell.getClassLoader().loadClass("WpdJobDataMap")
					def wpdJobDataMap = wpdJobDataMapClzz.newInstance()
					rv = jobInstance.execute(wpdJobDataMap)
				}catch(Throwable ta){
					// ignore
				}
			}
		})
		t.start()
		jobInstance.stop(t)
		//Job has been interrupted while sleeping so status should remain in false
		def isJobFinishedSuccessfully = jobInstance.isJobFinishedSuccessfully
		assertEquals(false, isJobFinishedSuccessfully)		
		//Finally assert the return value (null)
		assertEquals(rv, null)
	}
	
	@Test
	public void loadData() throws Exception {		
		
		def loader = shell.getClassLoader()
		loader.clearCache()
		def senderClzz = loader.loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		
		def jobFile = new File(path + "/src/resources/scheduledjob/job_normal.job")
		def clsJob = loader.parseClass(jobFile)
		 
		def jobInstance = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, sender)
		jobInstance.isJobFinishedSuccessfully = false
		println "Start executed....................." + jobFile.absolutePath
		def jobName = jobInstance.getName()
		
		
		def wpdJobDataMapClzz = shell.getClassLoader().loadClass("WpdJobDataMap")
		def wpdJobDataMap = wpdJobDataMapClzz.newInstance()
		
		def rv = jobInstance.execute(wpdJobDataMap)		
		println "After executed....................."
		def jobConfigLoaderClzz = loader.loadClass("MonitorJobConfigLoader")
		def jobConfigLoader = jobConfigLoaderClzz.newInstance(context)
		def resConstClzz = loader.loadClass("ResourceConstants")
		def props = jobConfigLoader.getProperties()
		
		def PERSISTENTDATA_File = new File(props.get(resConstClzz.MONITORJOBDATA_DIRECTORY) + "/monitorjobdata/PersistentData/" + jobName + ".txt")
		def prevOUTPUT_File = new File(props.get(resConstClzz.MONITORJOBDATA_DIRECTORY) + "/monitorjobdata/PrevOUTPUT/" + jobName + ".txt")
		def lastExecution_File = new File(props.get(resConstClzz.MONITORJOBDATA_DIRECTORY) + "/monitorjobdata/LastExecution/" + jobName + ".txt")		
		
		def PERSISTENTDATA = null
		PERSISTENTDATA = jobInstance.loadData(PERSISTENTDATA_File, jobInstance.persistentDataMap, jobName)		
		println "CONTENT PERSISTENTDATA: "+PERSISTENTDATA
		assertNotNull(PERSISTENTDATA)
		
		def prevOUTPUT = null
		prevOUTPUT = jobInstance.loadData(prevOUTPUT_File, jobInstance.prevOUTPUTMap, jobName)
		println "CONTENT prevOUTPUT: "+prevOUTPUT
		assertNotNull(prevOUTPUT)
	}	
	@Test
	public void loadParam() throws Exception{
		//jobName, instJobName, paramsInstances, properties		
		try{
		def loader = shell.getClassLoader()
		loader.clearCache()
		def senderClzz = loader.loadClass('DefaultSender')
		def sender = senderClzz.newInstance()
		
		def jobFile = new File(path + "/src/resources/scheduledjob/job_normal.job")
		def clsJob = loader.parseClass(jobFile)
		 
		def jobInstance = jobExecutableCls.newInstance(jobFile.absolutePath, clsJob, sender)
		jobInstance.isJobFinishedSuccessfully = false
		def jobName = jobInstance.getJobName()
		def wpdJobDataMapClzz = shell.getClassLoader().loadClass("WpdJobDataMap")
		def wpdJobDataMap = wpdJobDataMapClzz.newInstance()
		def rv = jobInstance.execute(wpdJobDataMap)
		
		def jobConfigLoaderClzz = loader.loadClass("MonitorJobConfigLoader")
		def jobConfigLoader = jobConfigLoaderClzz.newInstance(context)
		def resConstClzz = loader.loadClass("ResourceConstants")
		def properties = jobConfigLoader.getProperties()
		def result = jobInstance.loadParam(jobName, null, jobInstance.paramsInstances, properties)		
		}catch(Exception e){println e}
	}
}