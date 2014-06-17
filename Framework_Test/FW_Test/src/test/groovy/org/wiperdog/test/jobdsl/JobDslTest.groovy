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

//@RunWith(PaxExam.class)
//@ExamReactorStrategy(PerClass.class)
public class JobDslTest{
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
	
	//@Inject
	private org.osgi.framework.BundleContext context;
	
	//@Configuration
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
		mavenBundle("org.wiperdog", "org.wiperdog.jobmanager", "0.2.3-SNAPSHOT").startLevel(3),
		junitBundles()
		);
	}
	
	//@Before
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
			//-- Setting Groovy shell
			binding = new Binding()
			binding.setVariable("felix_home", path)
			RootLoader rootloader = new RootLoader(scriptpath123, lc.getClassLoader())
			shell = new GroovyShell(rootloader,binding)			
			jobDslInst = jobDslCls.newInstance(shell, jf, context)			
		}catch(Exception e){
		  println "***** "+e
		}	
		println "***** Complete setup phase!"
	}
	
	//@After
	public void shutdown() throws Exception {
		jf = null
		jobExecutableCls = null
		jobDslCls = null
		shell = null
		binding = null
		jobDslInst = null
	}
	
	//@Test	
	public void processJob()throws Exception {				
		println "***** Test creating job from file by JobDsl, job file locate at: "+path + "/src/resources/testJob.job"
		boolean ret = jobDslInst.processJob(new File(path + "/src/resources/jobdsl/testJob.job"))		
		assertEquals(true, ret)
	}
	
	//@Test
	public void processCls()throws Exception {
		println "***** Test creating job class from file by JobDsl, job class file locate at: "+path + "/src/resources/testClass.cls"
		boolean ret = jobDslInst.processCls(new File(path + "/src/resources/jobdsl/testClass.cls"))
		assertEquals(true, ret)		
	}
	//@Test
	public void processTrigger()throws Exception {
		println "***** Test creating job class from file by JobDsl, job class file locate at: "+path + "/src/resources/testTrigger.trg"
		boolean ret = jobDslInst.processTrigger(new File(path + "/src/resources/jobdsl/testTrigger.trg"))
		assertEquals(true, ret)	
	}
	//@Test
	public void processInstances()throws Exception {
		println "***** Test creating job class from file by JobDsl, job class file locate at: "+path + "/src/resources/testJob.instances"
		boolean ret = jobDslInst.processInstances(new File(path + "/src/resources/jobdsl/testJob.instances"))
		assertEquals(true, ret)	
	}
}