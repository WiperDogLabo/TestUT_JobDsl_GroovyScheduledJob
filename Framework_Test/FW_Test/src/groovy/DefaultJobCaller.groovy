
class DefaultJobCaller {
	def instanceJob
	def fileName
	def rootJobName
	def instanceName	
	def isJobFinishedSuccessfully = false
	//-- Sender will not be used
	def sender
	def DefaultJobCaller(objJob,fileName, rootJobName, instanceName, DefaultSender sender) {		
		this.instanceJob = objJob
		this.fileName = fileName
		this.rootJobName = rootJobName
		this.instanceName = instanceName	
		this.sender = sender
	}
	def start(sccontext, senderList) {
		try{
			def now = (new Date()).getTime().intdiv(1000)
			def binding = instanceJob.getBinding()
			def cFetchAction = null			
			def resultData = null			
			//Get FETCHACTION
			cFetchAction = getVarFromBinding(binding, ResourceConstants.DEF_FETCHACTION)			
			if (cFetchAction != null) {				
				resultData = runFetchAction (cFetchAction, null)				
			}
			isJobFinishedSuccessfully = true
			return resultData
		} catch (Exception e){
				throw e
				isJobFinishedSuccessfully = false
			return null
		}
	}
	def runFetchAction (fetchActionString, dbInfo) {		
		def resultData = null
		//Run FetchAction
		try {
			resultData = fetchActionString.call()
		} catch (Exception e) {			
			throw e
		}
		return resultData
	}
	def getVarFromBinding(binding, varKey){
		def var = null
		if (binding.hasVariable(varKey)) {
			var = binding.getVariable(varKey)
		}
		return var
	}
}
