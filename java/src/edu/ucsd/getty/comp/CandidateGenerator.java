package edu.ucsd.getty.comp;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import edu.ucsd.getty.ITraceFinder;
import edu.ucsd.getty.callgraph.CallGraph;
import edu.ucsd.getty.callgraph.CallGraphAnalyzer;
//import edu.ucsd.getty.callgraph.NameHandler;
import edu.ucsd.getty.utils.SetOperations;

public class CandidateGenerator implements ITraceFinder {
	
	private Set<String> changedMethods;
	private Set<String> allProjectMethods;
	private String binaryPath;
	private HashMap<String, Set<String>> typesToMethods;
	private CallGraph callgraph;

	public CandidateGenerator(
			Set<String> changed, String binaryPath, String packagePrefix) {
		this.changedMethods = changed;
		this.binaryPath = binaryPath;
		CallGraphAnalyzer analyzer = new CallGraphAnalyzer(packagePrefix);
		this.callgraph = analyzer.analyze(this.binaryPath);
		this.typesToMethods = analyzer.getTypesToMethods();
		this.allProjectMethods = analyzer.getAllProjectMethods();
	}
	
	public CandidateGenerator(
			Set<String> changed, String binaryPath) {
		this(changed, binaryPath, "");
	}

	public HashMap<String, Set<String>> getTypesToMethods(){
		return this.typesToMethods;
	}

	@Override
	public Set<String> getAllProjectMethods() {
		return this.allProjectMethods;
	}
	
//	private Set<String> reformat(Set<String> methodnames) {
//		Set<String> reformatted = new HashSet<String>();
//		for (String methodname : methodnames)
//			reformatted.add(NameHandler.internalToQualifiedName(methodname));
//		return reformatted;
//	}
//	
//	public Set<String> getReformattedCallersFor(String methodName) {
//		return reformat(getCallersFor(methodName));
//	}

	@Override
	public Set<String> getCallersFor(String methodName) {
		Set<String> callers = callgraph.getPossibleCallersOf(methodName);
		if (callers == null)
			return new HashSet<String>();
		else
			return SetOperations.intersection(callers, allProjectMethods);
	}

	@Override
	public Set<String> getCallersFor(Set<String> methodNames) {
		Set<String> callers = new HashSet<String>();
		for (String methodname : methodNames)
			callers.addAll(getCallersFor(methodname));
		return callers;
	}
	
	public Set<String> getCallers() {
		return getCallersFor(this.changedMethods);
	}
	
	@Override
	public Set<String> getCalleesFor(String methodName) {
		Set<String> callees = callgraph.getPossibleCalleesOf(methodName);
		if (callees == null)
			return new HashSet<String>();
		else
			return SetOperations.intersection(callees, allProjectMethods);
	}
	
	@Override
	public Set<String> getCalleesFor(Set<String> methodNames) {
		Set<String> callees = new HashSet<String>();
		for (String methodname : methodNames)
			callees.addAll(getCalleesFor(methodname));
		return callees;
	}
	
	public Set<String> getCallees() {
		return getCalleesFor(this.changedMethods);
	}
	
	@Override
	public Map<String, Map<String, Set<String>>> possibleOuterStreams() {
		if (this.changedMethods == null || this.changedMethods.isEmpty())
			return new HashMap<String, Map<String, Set<String>>>();
		else {
			Map<String, Map<String, Set<String>>> outerstreams = new HashMap<String, Map<String, Set<String>>>();
			for (String method : this.changedMethods) {
				Map<String, Set<String>> oses = new HashMap<String, Set<String>>();
				for (String caller : getCallersFor(method)) {
					Set<String> othercallees = getCalleesFor(caller);
					othercallees.remove(method);
					oses.put(caller, othercallees);
				}
				outerstreams.put(method, oses);
			}
			return outerstreams;
		}
	}
	
	@Override
	public Map<String, Set<String>> possibleInnerStreams() {
		if (this.changedMethods == null || this.changedMethods.isEmpty())
			return new HashMap<String, Set<String>>();
		else {
			Map<String, Set<String>> innerstreams = new HashMap<String, Set<String>>();
			for (String method : this.changedMethods)
				innerstreams.put(method, getCalleesFor(method));
			return innerstreams;
		}
	}
	
	@Override
	public Set<List<String>> getCandidateTraces(String methodName) {
		return getCandidateTraces(methodName, 
				new HashSet<String>(), new HashMap<String, Set<List<String>>>(), 0);
	}
	
	private final int LIMIT = 8;
	// Magic number -- don't ask me how I get this. Too many killing experiments.
	// 6: ~1s, 7: 1.5s, 8: 2.3s, 9: 20s
	private Set<List<String>> getCandidateTraces(String methodName, 
			Set<String> already, Map<String, Set<List<String>>> cache,
			int current_len) {
		current_len ++;
		if (current_len >= this.LIMIT) {
			Set<List<String>> more_end = new HashSet<List<String>>();
			List<String> end = new LinkedList<String>();
			end.add("!");
			more_end.add(end);
			return more_end;
		}
		
		if (cache.containsKey(methodName)) {
			return cache.get(methodName);
		} else {
			if (already.contains(methodName)) {
				List<String> recursion = new LinkedList<String>();
				recursion.add("@" + methodName);
				Set<List<String>> only = new HashSet<List<String>>();
				only.add(recursion);
				return only;
			} else {
				already.add(methodName);
				
				Set<List<String>> candidates = new HashSet<List<String>>();
				Set<String> callers = getCallersFor(methodName);
				
				if (callers.size() == 0) {
					List<String> single_chain = new LinkedList<String>();
					single_chain.add(methodName);
					candidates.add(single_chain);
				} else {					
					for (String caller : callers) {
						Set<List<String>> tailses = getCandidateTraces(caller, already, cache, current_len);
						for (List<String> tails : tailses) {
							List<String> chain = new LinkedList<String>();
							chain.add(methodName);
							chain.addAll(tails);
							candidates.add(chain);
						}
					}
				}
				
				already.remove(methodName);
				return candidates;
			}
		}
	}
	
	@Override
	public Map<String, Set<List<String>>> getCandidateTraces(Set<String> methods) {
		Map<String, Set<List<String>>> candidateMap = new HashMap<String, Set<List<String>>>();
		for (String method : methods){
			Set<List<String>> candidates = new HashSet<List<String>>();
			candidates.addAll(getCandidateTraces(method));
			candidateMap.put(method, candidates);
		}
		return candidateMap;
	}
	
	@Override
	public Map<String, Set<List<String>>> getCandidateTraces() {
		return getCandidateTraces(this.changedMethods);
	}
	
	public static void main(String[] args) {
		
		System.out.println("Getty - Candidate Generator");
		
		/**
		 * command to run this main:
		 * 		
		 * 		($process_dir = the directory to process
		 * 		 $mvn_build_path = the output of "mvn dependency:build-classpath"
		 * 		 $output_dir = the directory to place any output)
		 * 		
		 * 		java soot.Main -cp $process_dir:$mvn_build_path -pp -process-dir $process_dir -src-prec only-class -d $output_dir
		 * 
		 * 
		 * commands to get mvn variables:
		 * 
		 * 		($mvn_var is one of the following:
		 * 		 project.build.sourceDirectory
		 * 		 project.build.scriptSourceDirectory
		 * 		 project.build.testSourceDirectory
		 * 		 project.build.outputDirectory
		 * 		 project.build.testOutputDirectory
		 * 		 project.build.directory)
		 * 
		 * 		mvn help:evaluate -Dexpression=$mvn_var
		 * 
		 * command to build jar:
		 * 
		 * 		($target_jar_folder is the folder to save jar
		 * 		 $target_classes_folder is the folder with target classes)
		 * 
		 * 		jar cvf $target_jar_folder/target.jar -C $target_calsses_folder .
		 */
		
	}

}
