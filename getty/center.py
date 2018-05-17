# all Daikon's usage for invariant analysis

import re
import sys
import time
import json
from functools import partial
from multiprocessing import Pool
from os import path, makedirs

import agency
import config
from tools import daikon, ex, git, html, mvn, os, profiler


SHOW_DEBUG_INFO = config.show_debug_info
SHOW_MORE_DEBUG_INFO = config.show_debug_details


# relative path of getty output path (go), when pwd is root dir of project
def rel_go(go):
    if go.endswith("/"):
        go = go[:-1]
    lsi = go.rfind("/")
    return ".." + go[lsi:] + "/"


# sort invariants in the output invariant text file
def sort_txt_inv(out_file):
    inv_map = {}
    current_key = None
    with open(out_file, 'r+') as f:
        lines = f.read().strip().split("\n")
        if lines != ['']:
            for line in lines:
                line = line.strip()
                if line.startswith("================"):
                    current_key = None
                elif re.match(".*:::(ENTER|EXIT|CLASS|OBJECT|THROW).*", line):
                    current_key = line
                    inv_map[current_key] = []
                else:
                    inv_map[current_key].append(line)
        f.seek(0)
        f.truncate()
        if lines != [''] and len(inv_map):
            for title in sorted(inv_map):
                f.write("\n================\n")
                if title.endswith(":::EXIT"):
                    f.write(os.rreplace(title, ":::EXIT", ":::EXITSCOMBINED", 1) + "\n")
                else:
                    f.write(title + "\n")
                for inv in sorted(inv_map[title]):
                    f.write(inv + "\n")
        else:
            f.write('<NO INVARIANTS INFERRED>')


# get class-level expanded target set
def all_methods_expansion(candidates, target_set, go, this_hash, index, java_cmd, inv_gz):
    exp_tmp = go + "expansion_temp." + this_hash + "." + str(index) + ".allinvs"
    run_print_allinvs = " ".join([java_cmd, "daikon.PrintInvariants", "--output", exp_tmp, inv_gz])
    os.sys_call(run_print_allinvs, ignore_bad_exit=True)
    regex_header = "(.*):::(ENTER|EXIT|CLASS|OBJECT|THROW).*"
    with open(exp_tmp, 'r') as rf:
        alllines = rf.read().split("\n")
        for line in alllines:
            m = re.match(regex_header, line.strip())
            if m:
                full_method = m.group(1)
                leftp_bound = full_method.find("(")
                rightp_bound = full_method.find(")")
                if leftp_bound != -1:
                    all_dots_mtdname = full_method[:leftp_bound]
                    last_dot_index = all_dots_mtdname.rfind(".")
                    if last_dot_index != -1:
                        raw_method_name = all_dots_mtdname[last_dot_index+1:]
                        further_last_dot_index = all_dots_mtdname[:last_dot_index].rfind(".")
                        if all_dots_mtdname[further_last_dot_index+1:last_dot_index] == raw_method_name:
                            raw_method_name = "<init>"
                        candidates.add(
                            all_dots_mtdname[:last_dot_index] + ":" + raw_method_name +
                            full_method[leftp_bound:rightp_bound+1].replace(" ", ""))
    os.remove_file(exp_tmp)
    ex.save_list_to(go + config.expansion_tmp_files + "." + this_hash +
                        "." + str(index) + "." + str(int(time.time())),
                    candidates)


# v4. flexible to be run in parallel, in daikon-online mode
def seq_get_invs(target_set_index_pair, java_cmd, junit_torun, go, this_hash, consider_expansion):
    
    index = target_set_index_pair[1]
    target_set = target_set_index_pair[0]
    
#     select_pattern = daikon.select_full(target_set)
    select_pattern = daikon.dfformat_full_ordered(target_set)
    print "\n=== select pattern ===\n" + select_pattern + "\n"
    
    inv_gz = go + "_getty_inv_" + this_hash + "_." + index
    if config.compress_inv:
        inv_gz += ".inv.gz"
    else:
        inv_gz += ".inv"
    
    daikon_control_opt_list = []
    if SHOW_MORE_DEBUG_INFO:
        daikon_control_opt_list.append("--show_progress --no_text_output")
    elif SHOW_DEBUG_INFO:
        daikon_control_opt_list.append("--no_show_progress --no_text_output")
    else:
        daikon_control_opt_list.append("--no_text_output")
    if config.disable_known_invs:
        daikon_control_opt_list.append("--disable-all-invariants")
    if config.omit_redundant_invs:
        daikon_control_opt_list.append("--omit_from_output 0r")
    if config.daikon_format_only:
        daikon_control_opt_list.append("--format Daikon")
    daikon_control_opt_list.append(config.blocked_daikon_invs_exp)
    daikon_display_args = " ".join(daikon_control_opt_list)
    # run Chicory + Daikon (online) for invariants without trace I/O
    run_chicory_daikon = \
        " ".join([java_cmd, "daikon.Chicory --daikon-online --exception-handling",
                  "--daikon-args=\""+daikon_display_args,
                  "-o", inv_gz+"\"",
                  "--ppt-select-pattern=\""+select_pattern+"\"",
                  junit_torun])
    if SHOW_DEBUG_INFO:
        print "\n=== Daikon:Chicory+Daikon(online) command to run: \n" + run_chicory_daikon
    os.sys_call(run_chicory_daikon, ignore_bad_exit=True)
    
    expansion = set()
    if consider_expansion and config.class_level_expansion:
        try:
            all_methods_expansion(expansion, target_set, go, this_hash, index, java_cmd, inv_gz)
        except:
            pass
    
    if SHOW_DEBUG_INFO:
        current_count = 0
        total_count = len(target_set)
    
    all_to_consider = set(target_set)
    if config.class_level_expansion:
        all_to_consider = (all_to_consider | expansion)
    
    for tgt in all_to_consider:
        target_ff = daikon.fsformat_with_sigs(tgt)
        out_file = go+"_getty_inv__"+target_ff+"__"+this_hash+"_.inv.out"
        run_printinv = \
            " ".join([java_cmd, "daikon.PrintInvariants",
                      "--format", config.output_inv_format,
                      "--ppt-select-pattern=\'"+daikon.dpformat_with_sigs(tgt)+"\'",
                      "--output", out_file, inv_gz])
        if SHOW_DEBUG_INFO:
            current_count += 1
            if config.show_regex_debug:
                print "\n\tthe regex for: " + tgt + "\n\t\t" + daikon.dpformat_with_sigs(tgt) + "\n"
            os.print_progress(current_count, total_count, 
                              prefix='Progress('+index+'):', 
                              suffix='('+str(current_count)+'/'+str(total_count)+': '+tgt+')'+' '*20, 
                              bar_length=50)
        elif SHOW_MORE_DEBUG_INFO:
            print "\n=== Daikon:PrintInvs command to run: \n" + run_printinv
        os.sys_call(run_printinv, ignore_bad_exit=True)
        sort_txt_inv(out_file)
    os.remove_file(inv_gz)


def get_expansion_set(go):
    expansion = set([])
    try:
        files = os.from_sys_call(
            " ".join(["ls", go, "|", "grep", config.expansion_tmp_files])).strip().split("\n")
        for fl in files:
            fl = fl.strip()
            ep = set([])
            try:
                ep = ep | set(ex.read_str_from(go + fl))
            except:
                pass
            expansion = expansion | ep
        return expansion
    except:
        return expansion


# one pass template
def one_info_pass(
        junit_path, sys_classpath, agent_path, cust_mvn_repo, dyng_go, go, this_hash, target_set,
        changed_methods, changed_tests, inner_dataflow_methods, outer_dataflow_methods, json_filepath):
    os.sys_call("git checkout " + this_hash)
    os.sys_call("mvn clean")

    bin_path = mvn.path_from_mvn_call("outputDirectory")
    test_bin_path = mvn.path_from_mvn_call("testOutputDirectory")
    cp = mvn.full_classpath(junit_path, sys_classpath, bin_path, test_bin_path)
    if SHOW_DEBUG_INFO:
        print "\n===full classpath===\n" + cp + "\n"
    
    print "\ncopying all code to specific directory ...\n"
    all_code_dirs = [mvn.path_from_mvn_call("sourceDirectory"),
                     # mvn.path_from_mvn_call("scriptSourceDirectory"),
                     mvn.path_from_mvn_call("testSourceDirectory")]
    getty_code_store = go + '_getty_allcode_' + this_hash + '_/'
    print 'copy to ' + getty_code_store + '\n'
    makedirs(getty_code_store)
    for adir in all_code_dirs:
        os.sys_call(" ".join(["cp -r", adir + "/*", getty_code_store]), ignore_bad_exit=True)
    
    if config.use_special_junit_for_dyn:
        info_junit_path = os.rreplace(junit_path, config.default_junit_version, config.special_junit_version, 1)
        infocp = mvn.full_classpath(info_junit_path, sys_classpath, bin_path, test_bin_path)
    else:
        infocp = cp
    java_cmd = " ".join(["java", "-cp", infocp, 
#                         "-Xms"+config.min_heap, 
                         "-Xmx"+config.max_heap, 
                         "-XX:+UseConcMarkSweepGC", 
#                          "-XX:-UseGCOverheadLimit",
                         "-XX:-UseSplitVerifier",  # FIXME: JDK 8- only! 
                         ])
    
    # os.sys_call("mvn test -DskipTests", ignore_bad_exit=True)
    os.sys_call("mvn test-compile")
    
    junit_torun = mvn.junit_torun_str(cust_mvn_repo)
    junit_tests = junit_torun.split(" ")
    junit_to_run = junit_tests[0]
    if SHOW_DEBUG_INFO:
        print "\n===junit torun===\n" + junit_torun + "\n"
    
    #### dynamic run one round for all information    
    prefixes = daikon.common_prefixes(target_set)
    common_package = ''
    if len(prefixes) == 1:
        last_period_index = prefixes[0].rindex('.')
        if last_period_index > 0:
            # the common package should be at least one period away from the rest
            common_package = prefixes[0][:last_period_index]
    prefix_regexes = []
    for p in prefixes:
        prefix_regexes.append(p + "*")
    instrument_regex = "|".join(prefix_regexes)
    if SHOW_DEBUG_INFO:
        print "\n===instrumentation pattern===\n" + instrument_regex + "\n"
    # run tests with instrumentation
    run_instrumented_tests = \
        " ".join([java_cmd, "-ea",
                  "-javaagent:" + agent_path + "=\"" + instrument_regex + "\"",
                  junit_torun])
    if SHOW_DEBUG_INFO:
        print "\n=== Instrumented testing command to run: \n" + run_instrumented_tests
    
    if not path.exists(dyng_go):
        makedirs(dyng_go)
    
    full_info_exfile = go + "_getty_binary_info_" + this_hash + "_.ex"
    os.sys_call(run_instrumented_tests +
                    " > " + full_info_exfile +
                    ("" if config.show_stack_trace_info else " 2> /dev/null"),
                ignore_bad_exit=True)
    full_method_info_map = {}
    ext_start_index = len(config.method_info_line_prefix)
    with open(full_info_exfile, 'r') as f:
        contents = f.read().split("\n")
        for line in contents:
            line = line.strip()
            if line.startswith(config.method_info_line_prefix):
                rawdata = line[ext_start_index:]
                k, v = rawdata.split(" : ")
                full_method_info_map[k.strip()] = v.strip()

    os.merge_dyn_files(dyng_go, go, "_getty_dyncg_-hash-_.ex", this_hash)
    os.merge_dyn_files(dyng_go, go, "_getty_dynfg_-hash-_.ex", this_hash)

    ######getting method -> tests info
    fname =  go + "_getty_dyncg_" + this_hash + "_.ex"
    methods_to_tests = create_methods_to_tests(fname, junit_torun)

    #get types_to_methods
    types_to_methods = read_in_types_to_methods(go, this_hash)

    types_to_tests = {}
    #f = open(go + "_types_to_tests_" + this_hash + "_.ex", "w+")
    for key in types_to_methods.keys():
        for method in types_to_methods.get(key):
            method = method.strip("\n")
            method = method + "("
            for m in methods_to_tests.keys():
                method_name = m[:(len(method))]
                if method_name == method:
                    for test in methods_to_tests[m]:
                        if key in types_to_tests.keys():
                            types_to_tests[key].add(test)
                        else:
                            types_to_tests[key] = set([test])
    #For debugging
    # for key in types_to_tests.keys():
    #    for test in types_to_tests[key]:
    #        f.write(key + "," + test + "\n")
    # f.close()

    with open(json_filepath) as f:
        priorities = json.load(f)
    tests_to_run = set()
    types = set()
    new_target_set = set()
    for s in priorities["priorityList"]:
        for type in types_to_tests.keys():
            temp = type + ":"
            if s[:len(temp)] == temp:
                for method in types_to_methods[type]:
                    for m in methods_to_tests:
                        temp = method.strip("\n") + "("
                        if m[:len(temp)] == temp:
                            methodNumber = m.split("-")
                            new_target_set.add((method.strip("\n")) + "-" + methodNumber[1])
                for test in types_to_tests[type]:
                    tests_to_run.add(test)
                    types.add(type)
                    # print "s: " + s + "type: " + type + " test " + test
    ###########
    tests_for_junit = set()
    for test in tests_to_run:
        i = test.rfind(":")
        temp = test[:i]
        tests_for_junit.add(temp)
    for temp in tests_for_junit:
        junit_to_run = junit_to_run + " " + temp
    junit_torun = junit_to_run

    caller_of, callee_of = agency.caller_callee(go, this_hash)
    pred_of, succ_of = agency.pred_succ(go, this_hash)

    # add test methods into target set
    test_set = agency.get_test_set_dyn(new_target_set, callee_of, junit_torun)
    #test_set is correct
    # reset target set here
    refined_target_set, changed_methods, changed_tests = \
        agency.refine_targets(full_method_info_map, new_target_set, test_set,
                              caller_of, callee_of, pred_of, succ_of,
                              changed_methods, changed_tests,
                              inner_dataflow_methods, outer_dataflow_methods)
    profiler.log_csv(["method_count", "test_count", "refined_target_count"],
                     [[len(new_target_set), len(test_set), len(refined_target_set)]],
                     go + "_getty_y_method_count_" + this_hash + "_.profile.readable")
    
    git.clear_temp_checkout(this_hash)
    
    return common_package, test_set, refined_target_set, changed_methods, changed_tests, \
        cp, junit_torun, full_method_info_map


def read_in_types_to_methods(go, this_hash):
    types_to_methods = {}
    with open(go + "_types_to_methods_" + this_hash + "_.ex") as f:
        content = f.readlines()
    for line in content:
        pair = line.split(",")
        if pair[0] in types_to_methods.keys():
            types_to_methods[pair[0]].add(pair[1])
        else:
            types_to_methods[pair[0]] = set([pair[1]])
    return types_to_methods


def create_methods_to_tests(fname, junit_torun):
    methods_to_tests = {}
    with open(fname) as f:
        content = f.readlines()
    total_pairs = []
    nonTestMethodCalls = {}
    for line in content:
        line.strip("[()]")
        pairs = line.split("), (")
        total_pairs = total_pairs + pairs
    for pair in total_pairs:
        invocation = pair.split("\", ")
        for i in range(0, 2):
            invocation[i] = (invocation[i]).replace("\"", "")
        isATest = False
        testSuites = junit_torun.split(" ")
        for prefix in testSuites:
            prefix = prefix + ":"
            package = invocation[0][:(len(prefix))]
            if prefix == package:
                isATest = True
        if isATest:
            if invocation[1] in methods_to_tests.keys():
                methods_to_tests[invocation[1]].add(invocation[0])
            else:
                methods_to_tests[invocation[1]] = set([invocation[0]])
        else:
            if invocation[0] in nonTestMethodCalls.keys():
                for k in nonTestMethodCalls[invocation[0]]:
                    if k in nonTestMethodCalls:
                        nonTestMethodCalls[invocation[0]].union(nonTestMethodCalls[k])
            else:
                nonTestMethodCalls[invocation[0]] = set([invocation[1]])
        for caller in nonTestMethodCalls:
            for callee in nonTestMethodCalls[caller]:
                if callee in methods_to_tests and caller in methods_to_tests:
                    methods_to_tests[callee].union(methods_to_tests[caller])
                elif caller in methods_to_tests:
                    methods_to_tests[callee] = methods_to_tests[caller]
    return methods_to_tests


# one pass template
def one_inv_pass(go, cp, junit_torun, this_hash, refined_target_set, analysis_only=False):
    
    if not analysis_only:
        os.sys_call("git checkout " + this_hash)
    
    os.sys_call("mvn clean")
    
    if SHOW_DEBUG_INFO:
        print "\n===full classpath===\n" + cp + "\n"
    
    java_cmd = " ".join(["java", "-cp", cp, 
#                          "-Xms"+config.min_heap, 
                         "-Xmx"+config.max_heap, 
                         "-XX:+UseConcMarkSweepGC", 
#                          "-XX:-UseGCOverheadLimit",
                         "-XX:-UseSplitVerifier",  # FIXME: JDK 8- only! 
                         ])
    
    # os.sys_call("mvn test -DskipTests", ignore_bad_exit=True)
    os.sys_call("mvn test-compile")
    
    if SHOW_DEBUG_INFO:
        print "\n===junit torun===\n" + junit_torun + "\n"
    
    # v3.2, v4 execute with 4 core
    num_primary_workers = config.num_master_workers
    auto_parallel_targets = config.auto_fork
    slave_load = config.classes_per_fork
    target_map = daikon.target_s2m(refined_target_set)
    all_classes = target_map.keys()
    
    consider_expansion = (not analysis_only)
    
    if len(refined_target_set) <= num_primary_workers or (num_primary_workers == 1 and not auto_parallel_targets):
        single_set_tuple = (refined_target_set, "0")
        seq_get_invs(single_set_tuple, java_cmd, junit_torun, go, this_hash, consider_expansion)
    elif num_primary_workers > 1:  # FIXME: this distributation is buggy
        target_set_inputs = []
        all_target_set_list = list(refined_target_set)
        each_bulk_size = int(len(refined_target_set) / num_primary_workers)
        
        seq_func = partial(seq_get_invs, 
                           java_cmd=java_cmd, junit_torun=junit_torun, go=go, this_hash=this_hash,
                           consider_expansion=consider_expansion)
        for i in range(num_primary_workers):
            if not(i == num_primary_workers - 1):
                sub_list_tuple = (all_target_set_list[each_bulk_size*i:each_bulk_size*(i+1)], str(i))                
                target_set_inputs.append(sub_list_tuple)
            else:
                sub_list_tuple = (all_target_set_list[each_bulk_size*i:], str(i))
                target_set_inputs.append(sub_list_tuple)
        input_pool = Pool(num_primary_workers)
        input_pool.map(seq_func, target_set_inputs)
        input_pool.close()
        input_pool.join()
    elif num_primary_workers == 1 and auto_parallel_targets and slave_load >= 1:
        # elastic automatic processing
        target_set_inputs = []
        num_processes = 0
        
        # target_map has been calculated already
        # target_map = daikon.target_s2m(refined_target_set)
        # all_classes = target_map.keys()
        num_keys = len(all_classes)
        seq_func = partial(seq_get_invs, 
                           java_cmd=java_cmd, junit_torun=junit_torun, go=go, this_hash=this_hash,
                           consider_expansion=consider_expansion)
        
        for i in range(0, num_keys, slave_load):
            # (inclusive) lower bound is i
            # (exclusive) upper bound:
            j = min(i+slave_load, num_keys)
            sublist = []
            for k in range(i, j):
                the_key = all_classes[k]
                sublist.append(the_key)  # so it won't miss class/object invariants
                sublist += target_map[the_key]
            sublist_tuple = (sublist, str(num_processes))
            target_set_inputs.append(sublist_tuple)
            num_processes += 1
        
        max_parallel_processes = config.num_slave_workers
        if not analysis_only:
            profiler.log_csv(["class_count", "process_count", "max_parallel_processes", "slave_load"],
                             [[num_keys, num_processes, max_parallel_processes, slave_load]],
                             go + "_getty_y_elastic_count_" + this_hash + "_.profile.readable")
        
        input_pool = Pool(max_parallel_processes)
        input_pool.map(seq_func, target_set_inputs)
        input_pool.close()
        input_pool.join()
        
    else:
        print "\nIncorrect option for one center pass:"
        print "\tnum_primary_workers:", str(num_primary_workers)
        print "\tauto_parallel_targets:", str(auto_parallel_targets)
        print "\tslave_load", str(slave_load)
        sys.exit(1)
    
    if config.compress_inv:
        os.remove_many_files(go, "*.inv.gz")
    else:
        os.remove_many_files(go, "*.inv")
    
    # include coverage report for compare
    if config.analyze_test_coverage and not analysis_only:
        try:
            mvn.generate_coverage_report(go, this_hash)
        except:
            pass
    
    if not analysis_only:
        git.clear_temp_checkout(this_hash)
    
    
    if config.class_level_expansion:
        extra_expansion = get_expansion_set(go)
        os.remove_many_files(go, config.expansion_tmp_files + "*")
    else:
        extra_expansion = None
    
    return all_classes, extra_expansion


def mixed_passes(go, prev_hash, post_hash, refined_expansion_set,
                 refined_target_set, old_refined_target_set, new_refined_target_set,
                 old_cp, new_cp, old_junit_torun, new_junit_torun):
    if config.class_level_expansion:
        impact_set = refined_target_set | refined_expansion_set
    else:
        impact_set = refined_target_set
    # checkout old commit, then checkout new tests
    os.sys_call("git checkout " + prev_hash)
    new_test_path = mvn.path_from_mvn_call("testSourceDirectory")
    os.sys_call(" ".join(["git", "checkout", post_hash, new_test_path]))
#     # may need to check whether it is compilable, return code?
#     os.sys_call("mvn clean test-compile")
    one_inv_pass(go, new_cp, new_junit_torun,
                 prev_hash + "_" + post_hash,
                 impact_set, analysis_only=True)
    git.clear_temp_checkout(prev_hash)
    
    # checkout old commit, then checkout new src
    os.sys_call("git checkout " + prev_hash)
    new_src_path = mvn.path_from_mvn_call("sourceDirectory")
    os.sys_call(" ".join(["git", "checkout", post_hash, new_src_path]))
#     # may need to check whether it is compilable, return code?
#     os.sys_call("mvn clean test-compile")
    one_inv_pass(go, new_cp, old_junit_torun,
                 post_hash + "_" + prev_hash,
                 impact_set, analysis_only=True)
    git.clear_temp_checkout(prev_hash)


def __build_target2ln(infomap):
    result = {}
    for k in infomap:
        fullinfo = infomap[k]
        last_dash = fullinfo.rfind("-")
        result[fullinfo[:last_dash]] = fullinfo[last_dash+1:]
    return result


def __build_method2line(method_info_map):
    result = {}
    for k in method_info_map:
        full_method = method_info_map[k]
        last_dash = full_method.rfind("-")
        if last_dash != -1:
            result[full_method[:last_dash]] = full_method[last_dash+1:]
    return result


def __purify_targets(targets):
    result = set()
    for t in targets:
        last_dash_pos = t.rfind("-")
        if last_dash_pos == -1:
            result.add(t)
        else:
            result.add(t[:last_dash_pos])
    return result


def _merge_target_sets(old_rts, new_rts, old_mtd_info_map, new_mtd_info_map):
    result = set()
    old_mtd2ln = __build_target2ln(old_mtd_info_map)
    old_rts_purified = __purify_targets(old_rts)
    old_keyset = set(old_mtd2ln.keys())
    new_mtd2ln = __build_target2ln(new_mtd_info_map)
    new_rts_purified = __purify_targets(new_rts)
    new_keyset = set(new_mtd2ln.keys())
    for old_and_new in (old_rts_purified & new_rts_purified):
        mtd_full_info = old_and_new + "-" + old_mtd2ln[old_and_new] + "," + new_mtd2ln[old_and_new]
        result.add(mtd_full_info)
    for old_but_new in (old_rts_purified - new_rts_purified):
        if old_but_new in new_keyset:
            mtd_full_info = old_but_new + "-" + old_mtd2ln[old_but_new] + "," + new_mtd2ln[old_but_new]
        else:
            mtd_full_info = old_but_new + "-" + old_mtd2ln[old_but_new] + ",0"
        result.add(mtd_full_info)
    for new_but_old in (new_rts_purified - old_rts_purified):
        if new_but_old in old_keyset:
            mtd_full_info = new_but_old + "-" + old_mtd2ln[new_but_old] + "," + new_mtd2ln[new_but_old]
        else:
            mtd_full_info = new_but_old + "-0," + new_mtd2ln[new_but_old]
        result.add(mtd_full_info)
    return result


def _append_class_ln(class_set):
    result = set()
    for c in class_set:
        result.add(c + "-0,0")
    return result


def _common_specific_expansion(expansion, old_method_info_map, new_method_info_map):
    old_m2l = __build_method2line(old_method_info_map)
    new_m2l = __build_method2line(new_method_info_map)
    common_keys = set(old_m2l.keys()) & set(new_m2l.keys())
    result = set()
    for candidate in expansion:
        if candidate in common_keys:
            complete_info_name = candidate + "-" + old_m2l[candidate] + "," + new_m2l[candidate]
            result.add(complete_info_name)
    return result


# the main entrance
def visit(junit_path, sys_classpath, agent_path, cust_mvn_repo, separate_go, prev_hash, post_hash, targets, iso,
          old_changed_methods, old_changed_tests, old_inner_dataflow_methods, old_outer_dataflow_methods,
          new_changed_methods, new_changed_tests, new_inner_dataflow_methods, new_outer_dataflow_methods, json_filepath):
    
    dyng_go = separate_go[0]
    go = separate_go[1]
    
    print("\n****************************************************************");
    print("        Getty Center: Semantiful Differential Analyzer            ");
    print("****************************************************************\n");
    
    '''
        1-st pass: checkout prev_commit as detached head, and get new interested targets
    '''
    (old_common_package, old_test_set, old_refined_target_set,
     old_changed_methods, old_changed_tests, old_cp, old_junit_torun, old_method_info_map) = \
        one_info_pass(
            junit_path, sys_classpath, agent_path, cust_mvn_repo, dyng_go, go, prev_hash, targets,
            old_changed_methods, old_changed_tests, old_inner_dataflow_methods, old_outer_dataflow_methods, json_filepath)
    
    '''
        2-nd pass: checkout post_commit as detached head, and get new interested targets
    '''
    (new_common_package, new_test_set, new_refined_target_set,
     new_changed_methods, new_changed_tests, new_cp, new_junit_torun, new_method_info_map) = \
        one_info_pass(
            junit_path, sys_classpath, agent_path, cust_mvn_repo, dyng_go, go, post_hash, targets,
            new_changed_methods, new_changed_tests, new_inner_dataflow_methods, new_outer_dataflow_methods, json_filepath)


    '''
        middle pass: set common interests
    '''
    common_package = ''
    if old_common_package != '' and new_common_package != '':
        if (len(old_common_package) < len(new_common_package) and 
                (new_common_package+'.').find(old_common_package+'.') == 0):
            common_package = old_common_package
        elif (len(old_common_package) >= len(new_common_package) and 
                (old_common_package+'.').find(new_common_package+'.') == 0):
            common_package = old_common_package
    config.the_common_package.append(common_package)
#     refined_target_set = old_refined_target_set | new_refined_target_set
    refined_target_set, all_changed_methods, all_changed_tests = \
        _merge_target_sets(
            old_refined_target_set, new_refined_target_set, old_method_info_map, new_method_info_map), \
        _merge_target_sets(
            old_changed_methods, new_changed_methods, old_method_info_map, new_method_info_map), \
        _merge_target_sets(
            old_changed_tests, new_changed_tests, old_method_info_map, new_method_info_map)
    
    '''
        3-rd pass: checkout prev_commit as detached head, and get invariants for all interesting targets
    '''    
    old_all_classes, old_expansion = one_inv_pass(go,
        old_cp, old_junit_torun, prev_hash, refined_target_set)
    
    '''
        4-th pass: checkout post_commit as detached head, and get invariants for all interesting targets
    '''
    new_all_classes, new_expansion = one_inv_pass(go,
        new_cp, new_junit_torun, post_hash, refined_target_set)
    
    common_expansion = set()
    refined_expansion_set = set()
    if config.class_level_expansion:
        common_expansion = old_expansion & new_expansion
        refined_expansion_set = _common_specific_expansion(
            common_expansion, old_method_info_map, new_method_info_map)
    '''
        more passes: checkout mixed commits as detached head, and get invariants for all interesting targets
    '''
    if iso:
        mixed_passes(go, prev_hash, post_hash, refined_expansion_set,
                     refined_target_set, old_refined_target_set, new_refined_target_set,
                     old_cp, new_cp, old_junit_torun, new_junit_torun)
    
    '''
        last pass: set common interests
    '''
    html.src_to_html_ln_anchor(refined_target_set, go, prev_hash, for_old=True)
    html.src_to_html_ln_anchor(refined_target_set, go, post_hash)
    
    # should not need line number information anymore from this point on
    
    '''
        prepare to return
    '''
    all_classes_set = set(old_all_classes + new_all_classes)
    all_classes_set = _append_class_ln(all_classes_set)
    
    print 'Center analysis is completed.'
    return common_package, all_classes_set, refined_target_set, \
        old_test_set, old_refined_target_set, new_test_set, new_refined_target_set, \
        old_changed_methods, new_changed_methods, old_changed_tests, new_changed_tests, \
        all_changed_methods, all_changed_tests

