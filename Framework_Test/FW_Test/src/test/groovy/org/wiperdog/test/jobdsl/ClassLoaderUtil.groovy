package org.wiperdog.test.jobdsl



public class ClassLoaderUtil {
	//ClassLoader parent = Thread.currentThread().getContextClassLoader();
	private ClassLoader parent = getClass().getClassLoader();
	private GroovyClassLoader classLoader = new GroovyClassLoader(parent);
	
	public GroovyClassLoader getClassLoader() {
		return classLoader;
	}

	/**
	 * Load class
	 * @param path path to class need to load
	 * @return class
	 */
	public Class getCls(String path) {
		Class jobExecutableCls = classLoader.parseClass(new File(path));
		return jobExecutableCls;
	}
	
	/**
	 * Add URL to class loader before parsing class. Normally, it is an array of Groovy folders URL.
	 * @param urls
	 */
	public void addURL(URL[] urls){
		for(URL url:urls)
			classLoader.addURL(url);
	}
	
}
