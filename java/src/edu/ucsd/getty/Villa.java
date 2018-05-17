package edu.ucsd.getty;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.ucsd.getty.comp.ASTInspector;
import edu.ucsd.getty.comp.CandidateGenerator;
import edu.ucsd.getty.comp.InputDiffProcessor;
import edu.ucsd.getty.diff.GitDiff;
import edu.ucsd.getty.utils.DataStructureBuilder;
import edu.ucsd.getty.utils.SetOperations;

public class Villa {

	/**
	 * Input: diff file (path), target files (path), excluded test files (path), 
	 * 		  package (prefix) range, previous and current commit hashes
	 * Output: print candidate call chains
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("\n****************************************************************");
		System.out.println("Getty Villa: understand project differentials for method sets");
		System.out.println("****************************************************************\n");
		
		check_args(args);
		
		switch(args[0]) {
		
		/**
		 * simgen=old (DEFAULT, so, s)
		 * simgen=new (sn)
		 */
			case "-s":
			case "--simgen=bare":
			case "-so":
			case "--simgen":
			case "--simgen=old": 
			case "-sn":
			case "--simgen=new":
				execute_tour_simple_mode(args);
				break;
			
		/**
		 * comgen (c)
		 */
			case "-c":
			case "--comgen":
				execute_tour_complex_mode(args);
				break;
			
		/**
		 * comgen (c)
		 */
			case "-r":
			case "--recgen":
				execute_tour_recovery_mode(args);
				break;
			
		/**
		 * comgen (c)
		 */
			case "-l":
			case "-m":
			case "--l4m":
				extract_l4m_info(args);
				break;
			
		/**
		 * unrecognizable execution mode
		 */
			default:
				System.out.println("Unrecognizable first argument (execution mode): " + args[0]);
				print_help_info();
				System.exit(1);
				break;
		}
		
	}
	
	private static void check_args(String[] args) {
		if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
			print_help_info();
			System.exit(1);
		} else if (args.length == 7 || args.length == 9) {
			// tour mode argument check
			if (!(args[0].equals("--simgen=bare") || args[0].equals("--simgen=old") 
					|| args[0].equals("--simgen=new") || args[0].equals("--simgen") 
					|| args[0].equals("-s") || args[0].equals("-so")
					|| args[0].equals("-c") || args[0].equals("--comgen") 
					|| args[0].equals("-r") || args[0].equals("--recgen"))) {
				System.out.println("Incorrect execution mode: " + args[0]);
				print_help_info();
				System.exit(1);
			}
			if (args.length == 9 
					&& !(args[7].equals("-o") || args[7].equals("--output"))) {
				System.out.println("Incorrect secondary option: " + args[7]);
				print_help_info();
				System.exit(1);
			}
		} else if (args.length == 4 || args.length == 6) {
			if (!(args[0].equals("-l") || args[0].equals("-m") || args[0].equals("--l4m"))) {
				System.out.println("Incorrect execution mode: " + args[0]);
				print_help_info();
				System.exit(1);
			}
			if (args.length == 6
					&& !(args[4].equals("-o") || args[4].equals("--output"))) {
				System.out.println("Incorrect secondary option: " + args[4]);
				print_help_info();
				System.exit(1);
			}
		} else {
			System.out.println("Incorrect arguments given.");
			print_help_info();
			System.exit(1);
		}
	}
	
	private static void print_help_info() {
		System.out.println("Usage:"
				+ "\n\t  "
				+ "java -jar villa.jar <--help | -h>"
				+ "\n\t  "
				+ "java -jar villa.jar "
				+ "<--simgen=bare | -s> <diffpath> <targetpath> <testsrcrelpath> <pkgprefix | -> <prevcommit> <currcommit> "
				+ "[<--output | -o> <outputworkdir>]"
				+ "\n\t  "
				+ "java -jar villa.jar "
				+ "<--simgen=old | --simgen | -so> <diffpath> <targetpath> <testsrcrelpath> <pkgprefix | -> <prevcommit> <currcommit> "
				+ "[<--output | -o> <outputworkdir>]"
				+ "\n\t  "
				+ "java -jar villa.jar "
				+ "<--simgen=new | -sn> <diffpath> <targetpath> <testsrcrelpath> <pkgprefix | -> <prevcommit> <currcommit> "
				+ "[<--output | -o> <outputworkdir>]"
				+ "\n\t  "
				+ "java -jar villa.jar "
				+ "<--comgen | -c> <diffpath> <targetpath> <testsrcrelpath> <pkgprefix | -> <prevcommit> <currcommit> "
				+ "[<--output | -o> <outputworkdir>]"
				+ "\n\t  "
				+ "java -jar villa.jar "
				+ "<--recgen | -r> <diffpath> <targetpath> <testsrcrelpath> <pkgprefix | -> <prevcommit> <currcommit> "
				+ "[<--output | -o> <outputworkdir>]"
				+ "\n\t  "
				+ "java -jar villa.jar "
				+ "<--l4m | -l | -m> <srcpath> <testpath> <commit> "
				+ "[<--output | -o> <outputworkdir>]"
				+ "\n");
	}
	
	/**
	 * simgen=old (DEFAULT, so)
	 * simgen=new (sn)
	 * simgen=bare (s)
	 * 
	 * The simple mode to generate changed method set*, candidate call chains, 
	 * and all callers in the chains 
	 * 
	 * * In this mode, we consider only the current version
	 */
	protected static void execute_tour_simple_mode(String[] args) {
		String diff_path = args[1];
		String target_path = args[2];
		String test_path = args[3];
		String package_prefix = args[4].equals("-") ? "" : args[4];
		String prev_commit = args[5];
		String curr_commit = args[6];
		
		String output_dir = "/tmp/getty/";
		if (args.length == 9 && (args[7].equals("-o") || args[7].equals("--output"))) {
			output_dir = args[8];
			if(!output_dir.endsWith("/"))
				output_dir += "/";
		}
		
		try {
			/**********************************/
			String this_commit;
			Map<String, Integer[]> file_revision_lines;
			/**********************************/
			if (args[0].equals("-s") || args[0].equals("-so") 
					|| args[0].equals("--simgen=bare") 
					|| args[0].equals("--simgen") 
					|| args[0].equals("--simgen=old")) {				
				this_commit = prev_commit;
				file_revision_lines = get_original_file_lines_map(diff_path, prev_commit, curr_commit);
			} else {  // args[0].equals("-sn") || args[0].equals("--simgen=new")
				this_commit = curr_commit;
				file_revision_lines = get_revised_file_lines_map(diff_path, prev_commit, curr_commit);
			}
			
			Set<String> revised_methods = get_changed_src_methods(test_path, file_revision_lines, this_commit, output_dir);
//					System.out.println("changed methods: " + revised_methods + "\n");
			String chgmtd_out_path = output_dir + "_getty_chgmtd_src_";
			if (args[0].equals("-s") || args[0].equals("-so") 
					|| args[0].equals("--simgen") || args[0].equals("--simgen=old"))
				chgmtd_out_path += "old" + "_" + prev_commit + "_.ex";
			else  // args[0].equals("-sn") || args[0].equals("--simgen=new")
				chgmtd_out_path += "new" + "_" + curr_commit + "_.ex";
			System.out.println(
					"<simple mode>: number of changed methods: " + revised_methods.size() + "\n"
					+ "  output to file --> " + chgmtd_out_path + " ...\n");
			output_to(chgmtd_out_path, revised_methods);

			
			/**********************************/
			CandidateGenerator cGen = get_generator(target_path, package_prefix, revised_methods);
			ITraceFinder chain_generator = (ITraceFinder) cGen;

			try(FileWriter fw = new FileWriter(output_dir + "_types_to_methods_" + this_commit + "_.ex", true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
			{
				for (String key : cGen.getTypesToMethods().keySet()){
					for ( String method: cGen.getTypesToMethods().get(key)){
						out.println(key + "," + method);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(22);
			}

			Set<String> all_project_methods = chain_generator.getAllProjectMethods();
//					System.out.println(all_project_methods);
			String apm_out_path = output_dir + "_getty_allmtd_src_" + this_commit + "_.ex";
			System.out.println(
					"<simple mode>: number of all methods in project: " + all_project_methods.size() + "\n"
							+ "  output to file --> " + apm_out_path + " ...\n");
			output_to(apm_out_path, all_project_methods);
			
			// modified tests, inaccurate
//			Set<String> revised_tests = SetOperations.difference(
//					get_all_changed_methods(file_revision_lines, this_commit, output_dir),
//					all_project_methods);
			
			Set<String> all_that_changed = get_all_changed_methods(file_revision_lines, this_commit, output_dir);
			Set<String> revised_tests = new HashSet<String>();
			for (String one_changed : all_that_changed) {
				int last_dash_pos = one_changed.lastIndexOf("-");
				if (last_dash_pos != -1) {					
					String check_part = one_changed.substring(0, last_dash_pos);
					if (!all_project_methods.contains(check_part))
						revised_tests.add(check_part);
				}
			}
			
//			System.out.println("changed tests: " + revised_tests + "\n");
			String indicator = "";
			if (this_commit.equals(prev_commit))
				indicator = "old";
			else if (this_commit.equals(curr_commit))
				indicator = "new";
			else
				throw new Exception("simple mode usage error");
			String chgtests_out_path = output_dir + "_getty_chgmtd_test_" + indicator + "_" + this_commit + "_.ex";
			System.out.println(
					"<simple mode>: number of changed tests (inaccurate): " + revised_tests.size() + "\n"
							+ "  output to file --> " + chgtests_out_path + " ...\n");
			output_to(chgtests_out_path, revised_tests);
			
			
			////
			// get clr only if it is not bare mode
			////
			if (!(args[0].equals("-s") || args[0].equals("--simgen=bare"))) {
				output_dataflow_approx(output_dir, chain_generator, this_commit);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}
	
	/**
	 * comgen (c)
	 * 
	 * The complex mode to generate changed method set*, candidate call chains, 
	 * all callers in the chains, and all considered methods
	 * 
	 * * In this mode we consider not only the current version for precision
	 * * Assume simple mode has run
	 * 
	 * So far this mode only support forward analysis, i.e., from older version to newer.
	 */
	protected static void execute_tour_complex_mode(String[] args) {
		String diff_path = args[1];
		String target_path = args[2];
		String test_path = args[3];
		String package_prefix = args[4].equals("-") ? "" : args[4];
		String prev_commit = args[5];
		String curr_commit = args[6];
		
		String output_dir = "/tmp/getty/";
		if (args.length == 9 && (args[7].equals("-o") || args[7].equals("--output"))) {
			output_dir = args[8];
			if(!output_dir.endsWith("/"))
				output_dir += "/";
		}
		
		try {
			/**********************************/
			Map<String, Integer[]> file_revision_lines = get_revised_file_lines_map(diff_path, prev_commit, curr_commit);
			
			Map<String, Integer[]> file_revision_lines_fullbak = new HashMap<String, Integer[]>();
			file_revision_lines_fullbak.putAll(file_revision_lines);
			
			Set<String> revised_methods = get_changed_src_methods(test_path, file_revision_lines, curr_commit, output_dir);
//					System.out.println("changed methods: " + revised_methods + "\n");
			String chgmtd_out_path = output_dir + "_getty_chgmtd_src_" + "new" + "_" + curr_commit + "_.ex";
			System.out.println(
					"<complex mode>: number of changed methods: " + revised_methods.size() + "\n"
							+ "  output to file --> " + chgmtd_out_path + " ...\n");
			output_to(chgmtd_out_path, revised_methods);
			// will generate more accurate revised_methods set later, soon
			
			
			/**********************************/
			CandidateGenerator cGen = get_generator(target_path, package_prefix, revised_methods);
			ITraceFinder chain_generator = (ITraceFinder) cGen;

			try(FileWriter fw = new FileWriter(output_dir + "_types_to_methods_"+ curr_commit + "_.ex", true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
			{

				for (String key : cGen.getTypesToMethods().keySet()){
					for ( String method: cGen.getTypesToMethods().get(key)){
						out.println(key + "," + method);
					}
				}
				//more code
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(22);
			}

			Set<String> all_project_methods = chain_generator.getAllProjectMethods();
//					System.out.println(all_project_methods);
			String apm_out_path = output_dir + "_getty_allmtd_src_" + curr_commit + "_.ex";
			System.out.println(
					"<complex mode>: number of all methods in project: " + all_project_methods.size() + "\n"
							+ "  output to file --> " + apm_out_path + " ...\n");
			output_to(apm_out_path, all_project_methods);
			
			/********more precise revised method set********/
			Set<String> revised_methods_old = DataStructureBuilder.loadSetFrom(
					output_dir + "_getty_chgmtd_src_" + "old" + "_" + prev_commit + "_.ex");
			Set<String> possible_ignored_revised_methods = SetOperations.intersection(revised_methods_old, all_project_methods);
			
			// improved revised_methods
			revised_methods = SetOperations.union(revised_methods, possible_ignored_revised_methods);
			String improved_chgmtd_out_path = output_dir + "_getty_chgmtd_src_" + prev_commit + "_" + curr_commit + "_.ex";
			System.out.println(
					"<complex mode>: IMPROVED, number of changed methods: " + revised_methods.size() + "\n"
							+ "  output to file --> " + improved_chgmtd_out_path + " ...\n");
			output_to(improved_chgmtd_out_path, revised_methods);
			
			// removed methods
			Set<String> removed_methods = SetOperations.difference(revised_methods_old, all_project_methods);
			String removed_chgmtd_out_path = output_dir + "_getty_chgmtd_src_gone_" + prev_commit + "_" + curr_commit + "_.ex";
			System.out.println(
					"<complex mode>: IMPROVED, number of removed methods: " + removed_methods.size() + "\n"
							+ "  output to file --> " + removed_chgmtd_out_path + " ...\n");
			output_to(removed_chgmtd_out_path, removed_methods);
			
			// modified tests, inaccurate
//			Set<String> revised_tests = SetOperations.difference(
//					get_all_changed_methods(file_revision_lines_fullbak, curr_commit, output_dir),
//					all_project_methods);
			
			Set<String> all_that_changed = get_all_changed_methods(file_revision_lines_fullbak, curr_commit, output_dir);
			Set<String> revised_tests = new HashSet<String>();
			for (String one_changed : all_that_changed) {
				int last_dash_pos = one_changed.lastIndexOf("-");
				if (last_dash_pos != -1) {					
					String check_part = one_changed.substring(0, last_dash_pos);
					if (!all_project_methods.contains(check_part))
						revised_tests.add(check_part);
				}
			}
			
//			System.out.println("changed tests: " + revised_tests + "\n");
			String chgtests_out_path = output_dir + "_getty_chgmtd_test_" + "new" + "_" + curr_commit + "_.ex";
			System.out.println(
					"<complex mode>: number of changed tests (inaccurate): " + revised_tests.size() + "\n"
							+ "  output to file --> " + chgtests_out_path + " ...\n");
			output_to(chgtests_out_path, revised_tests);
			
			/************************************************/
			ITraceFinder chain_generator_improved = (ITraceFinder) get_generator(target_path, package_prefix, revised_methods);
			output_dataflow_approx(output_dir, chain_generator_improved, curr_commit);

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}
	
	/**
	 * recgen (c)
	 * 
	 * The recovery mode to generate candidate call chains and all callers in the chains
	 * 
	 * * In this mode we consider not only the old version for precision
	 * * Assume both simple mode and complex mode have run
	 * 
	 * So far this mode only support backward analysis, i.e., from newer version to older.
	 */
	protected static void execute_tour_recovery_mode(String[] args) {
		String target_path = args[2];
		String package_prefix = args[4].equals("-") ? "" : args[4];
		String prev_commit = args[5];
		String curr_commit = args[6];
		
		String output_dir = "/tmp/getty/";
		if (args.length == 9 && (args[7].equals("-o") || args[7].equals("--output"))) {
			output_dir = args[8];
			if(!output_dir.endsWith("/"))
				output_dir += "/";
		}
		
		try {
			/********more precise revised method set********/
			Set<String> revised_methods_old = DataStructureBuilder.loadSetFrom(
					output_dir + "_getty_chgmtd_src_" + "old" + "_" + prev_commit + "_.ex");
			Set<String> revised_methods_new = DataStructureBuilder.loadSetFrom(
					output_dir + "_getty_chgmtd_src_" + "new" + "_" + curr_commit + "_.ex");
			Set<String> all_project_methods = DataStructureBuilder.loadSetFrom(
					output_dir + "_getty_allmtd_src_" + prev_commit + "_.ex");
			Set<String> possible_ignored_revised_methods = SetOperations.intersection(revised_methods_new, all_project_methods);
			//

			// improved revised_methods
			Set<String> revised_methods = SetOperations.union(revised_methods_old, possible_ignored_revised_methods);
			String improved_chgmtd_out_path = output_dir + "_getty_chgmtd_src_" + curr_commit + "_" + prev_commit + "_.ex";
			System.out.println(
					"<recovery mode>: IMPROVED, number of changed methods: " + revised_methods.size() + "\n"
							+ "  output to file --> " + improved_chgmtd_out_path + " ...\n");
			output_to(improved_chgmtd_out_path, revised_methods);
			
			// added methods
			Set<String> added_methods = SetOperations.difference(revised_methods_new, all_project_methods);
			String added_chgmtd_out_path = output_dir + "_getty_chgmtd_src_gain_" + prev_commit + "_" + curr_commit + "_.ex";
			System.out.println(
					"<recovery mode>: IMPROVED, number of added methods: " + added_methods.size() + "\n"
							+ "  output to file --> " + added_chgmtd_out_path + " ...\n");
			output_to(added_chgmtd_out_path, added_methods);

			ITraceFinder chain_generator_improved = (ITraceFinder) get_generator(target_path, package_prefix, revised_methods);
			output_dataflow_approx(output_dir, chain_generator_improved, prev_commit);
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}
	
	private static void extract_l4m_info(String[] args) {
		String src_path = args[1];
		String test_path = args[2];
		String the_hash = args[3];
		try {
			Map<String, Integer> l4ms = ASTInspector.getMethodLineNumberMap(src_path, ".java");
			if (!test_path.equals(src_path))
				l4ms.putAll(ASTInspector.getMethodLineNumberMap(test_path, ".java"));
			String output_dir = "/tmp/getty/";
			if (args.length == 6 && (args[4].equals("-o") || args[4].equals("--output"))) {
				output_dir = args[5];
				if (!output_dir.endsWith("/"))
					output_dir += "/";
			}
			
			String l4ms_str = "{";
			for (String method : l4ms.keySet()) {
				l4ms_str += ("\"" + method + "\": ");
				l4ms_str += (l4ms.get(method) + ", ");
			}
			l4ms_str += "}";
			
			String output_path = output_dir + "_getty_alll4m_" + the_hash + "_.ex";
			System.out.println(
					"<line# for methods>: number of methods with line# information: " + l4ms.size() + "\n"
							+ "  output to file --> " + output_path + " ...\n");
			PrintWriter l4ms_out = new PrintWriter(
					new BufferedWriter(new FileWriter(output_path, false)));
			l4ms_out.print(l4ms_str);
			l4ms_out.close();
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}
	
	private static void output_dataflow_approx(String output_dir, ITraceFinder generator, String commit_hash) {
		try {
			// output inner flow candidates
			Map<String, Set<String>> inner_streams = generator.possibleInnerStreams();
			String is_str = "{";
			for (String method : inner_streams.keySet()) {
				is_str += ("\"" + method + "\": [");
				for (String inner_callee : inner_streams.get(method)) {
					is_str += ("\"" + inner_callee + "\", ");
				}
				is_str += "], ";
			}
			is_str += "}";
			String is_out_path = output_dir + "_getty_dfinner_" + commit_hash + "_.ex";
			System.out.println(
					"<dataflow approximate>: number of project methods considered for inner flows: " + inner_streams.size() + "\n"
							+ "  output to file --> " + is_out_path + " ...\n");
			PrintWriter is_out = new PrintWriter(
					new BufferedWriter(new FileWriter(is_out_path, false)));
			is_out.print(is_str);
			is_out.close();
			
			// output outer flow candidates
			Map<String, Map<String, Set<String>>> outer_streams = generator.possibleOuterStreams();
			String os_str = "{";
			for (String method : outer_streams.keySet()) {
				os_str += ("\"" + method + "\": {");
				Map<String, Set<String>> caller_callees = outer_streams.get(method);
				for (String caller : caller_callees.keySet()) {
					os_str += ("\"" + caller + "\": [");
					for (String other_callee : caller_callees.get(caller))
						os_str += ("\"" + other_callee + "\", ");
					os_str += "], ";
				}
				os_str += "}, ";
			}
			os_str += "}";
			String os_out_path = output_dir + "_getty_dfouter_" + commit_hash + "_.ex";
			System.out.println(
					"<dataflow approximate>: number of project methods considered for outer flows: " + outer_streams.size() + "\n"
							+ "  output to file --> " + os_out_path + " ...\n");
			PrintWriter os_out = new PrintWriter(
					new BufferedWriter(new FileWriter(os_out_path, false)));
			os_out.print(os_str);
			os_out.close();
			
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(2);
		}
		
	}
	
	private static void output_to(String out_path, Set<String> set_content) throws IOException {
		PrintWriter out_file = new PrintWriter(
				new BufferedWriter(new FileWriter(out_path, false)));
		String str_content = "[";
		for (String method : set_content) {
			str_content += ("\"" + method + "\", ");
		}
		str_content += "]";
		out_file.print(str_content);
		out_file.close();
	}
	
	private static CandidateGenerator get_generator(String target_path, String package_prefix, Set<String> revised_methods) {
		System.out.println("\nGetting all project methods, call graphs and candidate call chains ...\n");
		CandidateGenerator chain_generator = new CandidateGenerator(revised_methods, target_path, package_prefix);
		return chain_generator;
	}
	
	private static Set<String> get_all_changed_methods(
			Map<String, Integer[]> file_revision_lines, String commit_hash, String output_dir) {
		System.out.println("\nGetting changed methods (in .java files only, not excluding tests) ...\n");
		IMethodRecognizer ast_inspector = new ASTInspector();
		Set<String> exclusion = new HashSet<String>();
//		System.out.println("DEBUG -- before exclusion: " + file_revision_lines.keySet());
		for (String file : file_revision_lines.keySet())
			if (!file.endsWith(".java"))
				exclusion.add(file);
		for (String ext : exclusion)
			file_revision_lines.remove(ext);
//		System.out.println("DEBUG -- after exclusion: " + file_revision_lines.keySet());
			
		Set<String> revised_methods = ast_inspector.changedMethods(file_revision_lines);
		
		return revised_methods;
	}

	private static Set<String> get_changed_src_methods(
			String test_path, Map<String, Integer[]> file_revision_lines, String commit_hash, String output_dir) {
		System.out.println("\nGetting changed methods (in .java files only, excluding tests) ...\n");
		IMethodRecognizer ast_inspector_lm = new ASTInspector();
		Set<String> exclusion_lm = new HashSet<String>();
		
		// get all l2m and m2l for both src and test
		for (String file : file_revision_lines.keySet())
			if (!file.endsWith(".java"))  // remove all non-java files
				exclusion_lm.add(file);
		for (String ext : exclusion_lm)
			file_revision_lines.remove(ext);
		ast_inspector_lm.changedMethods(file_revision_lines);
		Map<String, String> l2m = ast_inspector_lm.l2m();
		Map<String, Set<String>> m2l = ast_inspector_lm.m2l();
		output_m2l_l2m(output_dir, l2m, m2l, commit_hash);
		
		// get all revised methods in src
		IMethodRecognizer ast_inspector = new ASTInspector();
		Set<String> exclusion = new HashSet<String>();
		for (String file : file_revision_lines.keySet())
			if (file.startsWith(test_path))  // remove all test files
				exclusion.add(file);
		for (String ext : exclusion)
			file_revision_lines.remove(ext);
		
		return ast_inspector.changedMethods(file_revision_lines);
	}
	
	private static void output_m2l_l2m(
			String output_dir, Map<String, String> l2m, Map<String, Set<String>> m2l, String commit_hash) {
		try {			
			// output l2m (line number to method) information
			String l2m_out_path = output_dir + "_getty_fl2m_" + commit_hash + "_.ex";
			System.out.println(
					"<one-time l2m>: number of line number to method entries: " + l2m.size() + "\n"
					+ "  output to file --> " + l2m_out_path + " ...\n");
			PrintWriter l2m_out_file = new PrintWriter(
					new BufferedWriter(new FileWriter(l2m_out_path, false)));
			String l2m_content = "{";
			for (String fl : l2m.keySet()) {
				String[] file_line = fl.split(",");
				String key = "(\"" + file_line[0] + "\", " + file_line[1] + ")";
				String value = "\"" + l2m.get(fl) + "\"";
				l2m_content += (key + ": " + value + ", ");
			}
			l2m_content += "}";
			l2m_out_file.print(l2m_content);
			l2m_out_file.close();
			
			// output m2l (method to line numbers) information
			String m2l_out_path = output_dir + "_getty_fm2l_" + commit_hash + "_.ex";
			System.out.println(
					"<one-time m2l>: number of method to line number entries: " + m2l.size() + "\n"
					+ "  output to file --> " + m2l_out_path + " ...\n");
			PrintWriter m2l_out_file = new PrintWriter(
					new BufferedWriter(new FileWriter(m2l_out_path, false)));
			String m2l_content = "{";
			for (String m : m2l.keySet()) {
				String key = "\"" + m + "\"";
				m2l_content += (key + ": [");
				for (String fl : m2l.get(m)) {
					String[] file_line = fl.split(",");
					String value = "(\"" + file_line[0] + "\", " + file_line[1] + ")";
					m2l_content += (value + ", ");
				}
				m2l_content += "], ";
			}
			m2l_content += "}";
			m2l_out_file.print(m2l_content);
			m2l_out_file.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(2);
		}
	}

	private static Map<String, Integer[]> get_revised_file_lines_map(String diff_path, String prev_commit, String curr_commit)
			throws Exception {
		System.out.println("Parsing differential file for the revised ...\n");
		IInputProcessor diff_processor = new InputDiffProcessor();
		GitDiff git_diff = diff_processor.parseDiff(diff_path, prev_commit, curr_commit);
		Map<String, Integer[]> file_revision_lines = diff_processor.newLinesRevised(git_diff);
		return file_revision_lines;
	}
	
	private static Map<String, Integer[]> get_original_file_lines_map(String diff_path, String prev_commit, String curr_commit)
			throws Exception {
		System.out.println("Parsing differential file for the original ...\n");
		IInputProcessor diff_processor = new InputDiffProcessor();
		GitDiff git_diff = diff_processor.parseDiff(diff_path, prev_commit, curr_commit);
		Map<String, Integer[]> file_revision_lines = diff_processor.oldLinesRevised(git_diff);
		return file_revision_lines;
	}

}
