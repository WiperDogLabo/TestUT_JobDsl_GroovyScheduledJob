package org.wiperdog.test.jobdsl



public class ClassLoaderUtil {
	ClassLoader parent = getClass().getClassLoader()
	GroovyClassLoader loader = null
	//ClassLoader parent = Thread.currentThread().getContextClassLoader();
	
	public ClassLoaderUtil(ClassLoader parent) {
		this.parent = parent
		this.loader = new GroovyClassLoader(this.parent);
		
	}
	public ClassLoaderUtil() {
		if(loader == null ){			
			loader = new GroovyClassLoader(parent)
		}
	}
	public GroovyClassLoader getClzzLoader() {
		if(loader == null ){			
			loader = new GroovyClassLoader(parent)
		}
		return loader
	}

	/**
	 * Load class
	 * @param path path to class need to load
	 * @return class
	 */
	public Class getCls(String path) {				
		if(loader == null ){			
			loader = new GroovyClassLoader(parent)
		}
		Class clzz = loader.parseClass(new File(path))	
		return clzz
	}
	
	
	/**
	 * Add URL to class loader before parsing class. Normally, it is an array of Groovy folders URL.
	 * @param urls
	 */
	public void addURL(URL[] urls){
		for(URL url:urls)
			loader.addURL(url)
	}
	
}
