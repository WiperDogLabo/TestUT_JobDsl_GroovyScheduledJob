package org.wiperdog.test.jobdsl;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;
import groovy.lang.*;
import groovy.util.*;

@org.junit.runner.RunWith(value=org.ops4j.pax.exam.junit.PaxExam.class) @org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy(value=org.ops4j.pax.exam.spi.reactors.PerClass.class) public class GroovyScheduledJobTest
  extends java.lang.Object  implements
    groovy.lang.GroovyObject {
public static final java.lang.String PATH_TO_JOBCLASS = "src/groovy/GroovyScheduledJob.groovy";
public  groovy.lang.MetaClass getMetaClass() { return (groovy.lang.MetaClass)null;}
public  void setMetaClass(groovy.lang.MetaClass mc) { }
public  java.lang.Object invokeMethod(java.lang.String method, java.lang.Object arguments) { return null;}
public  java.lang.Object getProperty(java.lang.String property) { return null;}
public  void setProperty(java.lang.String property, java.lang.Object value) { }
public  java.lang.String getPath() { return (java.lang.String)null;}
public  void setPath(java.lang.String value) { }
public  java.lang.Object getJf() { return null;}
public  void setJf(java.lang.Object value) { }
public  java.lang.Class getJobExecutableCls() { return (java.lang.Class)null;}
public  void setJobExecutableCls(java.lang.Class value) { }
public  java.lang.Object getJobExecutableInst() { return null;}
public  void setJobExecutableInst(java.lang.Object value) { }
public  java.lang.Object getShell() { return null;}
public  void setShell(java.lang.Object value) { }
public  java.lang.Object getBinding() { return null;}
public  void setBinding(java.lang.Object value) { }
public  org.wiperdog.test.jobdsl.ClassLoaderUtil getLc() { return (org.wiperdog.test.jobdsl.ClassLoaderUtil)null;}
public  void setLc(org.wiperdog.test.jobdsl.ClassLoaderUtil value) { }
public  java.lang.Object InterruptJobTest() { return null;}
@org.ops4j.pax.exam.Configuration() public  org.ops4j.pax.exam.Option[] config() { return (org.ops4j.pax.exam.Option[])null;}
@org.junit.Before() public  void setup()throws java.lang.Exception { }
@org.junit.After() public  void shutdown()throws java.lang.Exception { }
public  void getJObInstance_normal()throws java.lang.Exception { }
public  void getJObInstance_instances()throws java.lang.Exception { }
public  void getJobClassName()throws java.lang.Exception { }
public  void execute()throws java.lang.Exception { }
public  void getJobExecutedStatus()throws java.lang.Exception { }
public  void stop()throws java.lang.Exception { }
@org.junit.Test() public  void loadData()throws java.lang.Exception { }
public  void loadParams()throws java.lang.Exception { }
}
