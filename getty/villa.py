from os import chdir

import config
from tools import ex, git, mvn, os


def checkout_build(proj_dir, commit_hash):
    os.sys_call("git checkout " + commit_hash)
    os.sys_call("mvn clean")
    bin_path = mvn.path_from_mvn_call("outputDirectory")
    src_rel_path = mvn.path_from_mvn_call("sourceDirectory")
    if src_rel_path.startswith(proj_dir):
        src_rel_path = src_rel_path[len(proj_dir):]
    else:
        raise ValueError("proj_dir is not a prefix of src path")
    print "current src path (relative): " + src_rel_path + "\n"
    test_src_rel_path = mvn.path_from_mvn_call("testSourceDirectory")
    if test_src_rel_path.startswith(proj_dir):
        test_src_rel_path = test_src_rel_path[len(proj_dir):]
    else:
        raise ValueError("proj_dir is not a prefix of test src path")
    print "current test src path (relative): " + test_src_rel_path + "\n"
    os.sys_call("mvn test-compile")
    return bin_path, src_rel_path, test_src_rel_path


def visit(villa_path, pwd, proj_dir, go, prev_hash, post_hash, pkg_prefix="-"):
    print("\n****************************************************************");
    print("        Getty Villa: Semantiful Differential Analyzer             ");
    print("****************************************************************\n");
    
    print "current working directory: " + pwd + "\n"
    
    diff_out = go + "text.diff"
    os.sys_call(" ".join(["git diff",
                          str(config.git_diff_extra_ops),
                          "{0} {1} > {2}"]).format(prev_hash, post_hash, diff_out))
    
    '''
        1-st pass: checkout prev_commit as detached head, and get all sets and etc, in simple (bare) mode (-s)
            remember to clear after this pass
    '''
    bin_path, src_rel_path, test_src_rel_path = checkout_build(proj_dir, prev_hash)
    print "******" + go
    print "******" + prev_hash
    print "******" + post_hash
    run_villa = "java -jar {0} -s {1} {2} {3} {4} {5} {6} -o {7}".format(
        villa_path, diff_out, bin_path, test_src_rel_path, pkg_prefix, prev_hash, post_hash, go)
    run_villa_l4ms = "java -jar {0} -l {1} {2} {3} -o {4}".format(
        villa_path, src_rel_path, test_src_rel_path, prev_hash, go)
    print "\n\nstart to run Villa ... \n\n" + run_villa + "\n  and  \n" + run_villa_l4ms
    chdir(proj_dir)
    os.sys_call(run_villa)
    os.sys_call(run_villa_l4ms)
    chdir(pwd)
    
    old_changed_methods = ex.read_str_from(go + "_getty_chgmtd_src_old_{0}_.ex".format(prev_hash))
    old_all_methods = ex.read_str_from(go + "_getty_allmtd_src_{0}_.ex".format(prev_hash))
    old_l2m = ex.read_str_from(go + "_getty_fl2m_{0}_.ex".format(prev_hash))
    old_m2l = ex.read_str_from(go + "_getty_fm2l_{0}_.ex".format(prev_hash))
    old_changed_tests = ex.read_str_from(go + "_getty_chgmtd_test_old_{0}_.ex".format(prev_hash))
#     # DEBUG ONLY
#     print old_changed_methods
#     print len(old_all_methods)
#     print old_l2m
#     print old_m2l
#     print old_changed_tests
    
    git.clear_temp_checkout(prev_hash)
    
    '''
        2-nd pass: checkout post_commit as detached head, and get all sets and etc, in complex mode (-c)
            remember to clear after this pass
    '''
    bin_path, src_rel_path, test_src_rel_path = checkout_build(proj_dir, post_hash)
    
    run_villa = "java -jar {0} -c {1} {2} {3} {4} {5} {6} -o {7}".format(
        villa_path, diff_out, bin_path, test_src_rel_path, pkg_prefix, prev_hash, post_hash, go)
    run_villa_l4ms = "java -jar {0} -l {1} {2} {3} -o {4}".format(
        villa_path, src_rel_path, test_src_rel_path, post_hash, go)
    print "\n\nstart to run Villa ... \n\n" + run_villa + "\n  and  \n" + run_villa_l4ms
    chdir(proj_dir)
    os.sys_call(run_villa)
    os.sys_call(run_villa_l4ms)
    chdir(pwd)
    
    new_changed_methods = ex.read_str_from(go + "_getty_chgmtd_src_new_{0}_.ex".format(post_hash))
    new_improved_changed_methods = ex.read_str_from(go + "_getty_chgmtd_src_{0}_{1}_.ex".format(prev_hash, post_hash))
    new_removed_changed_methods = ex.read_str_from(go + "_getty_chgmtd_src_gone_{0}_{1}_.ex".format(prev_hash, post_hash))
    # TODO or FIXME
    # new_all_ccc_related = ex.read_str_from(go + "_getty_cccmtd_{0}_.ex".format(post_hash))  # not needed for now
    # new_all_cccs = ex.read_str_from(go + "_getty_ccc_{0}_.ex".format(post_hash))  # not needed for now
    new_all_methods = ex.read_str_from(go + "_getty_allmtd_src_{0}_.ex".format(post_hash))
    new_l2m = ex.read_str_from(go + "_getty_fl2m_{0}_.ex".format(post_hash))
    new_m2l = ex.read_str_from(go + "_getty_fm2l_{0}_.ex".format(post_hash))
    new_inner_dataflow_methods = ex.read_str_from(go + "_getty_dfinner_{0}_.ex".format(post_hash))
    new_outer_dataflow_methods = ex.read_str_from(go + "_getty_dfouter_{0}_.ex".format(post_hash))
    new_changed_tests = ex.read_str_from(go + "_getty_chgmtd_test_new_{0}_.ex".format(post_hash))
#     # DEBUG ONLY
#     print new_changed_methods
#     print new_improved_changed_methods
#     print new_removed_changed_methods
#     print new_all_ccc_related
#     print new_all_cccs
#     print len(new_all_methods)
#     print new_l2m
#     print new_m2l
#     print new_inner_dataflow_methods
#     print new_outer_dataflow_methods
#     print new_changed_tests
    
    git.clear_temp_checkout(post_hash)
    
    '''
        3-rd pass: checkout prev_commit as detached head, and get all sets and etc, in recovery mode (-r)
            remember to clear after this pass
    '''
    bin_path, src_rel_path, test_src_rel_path = checkout_build(proj_dir, prev_hash)
    
    run_villa = "java -jar {0} -r {1} {2} {3} {4} {5} {6} -o {7}".format(
        villa_path, diff_out, bin_path, test_src_rel_path, pkg_prefix, prev_hash, post_hash, go)
    print "\n\nstart to run Villa ... \n\n" + run_villa
    chdir(proj_dir)
    os.sys_call(run_villa)
    chdir(pwd)
    
    old_improved_changed_methods = ex.read_str_from(go + "_getty_chgmtd_src_{1}_{0}_.ex".format(prev_hash, post_hash))
    old_added_changed_methods = ex.read_str_from(go + "_getty_chgmtd_src_gain_{0}_{1}_.ex".format(prev_hash, post_hash))
    # TODO or FIXME
    # old_all_ccc_related = ex.read_str_from(go + "_getty_cccmtd_{0}_.ex".format(prev_hash))  # not needed for now
    # old_all_cccs = ex.read_str_from(go + "_getty_ccc_{0}_.ex".format(prev_hash))  # not needed for now
    old_inner_dataflow_methods = ex.read_str_from(go + "_getty_dfinner_{0}_.ex".format(prev_hash))
    old_outer_dataflow_methods = ex.read_str_from(go + "_getty_dfouter_{0}_.ex".format(prev_hash))
#     # DEBUG ONLY
#     print old_changed_methods
#     print old_improved_changed_methods
#     print old_added_changed_methods
#     print old_all_ccc_related
#     print old_all_cccs
#     print len(old_all_methods)
#     print old_inner_dataflow_methods
#     print old_outer_dataflow_methods
    
    git.clear_temp_checkout(prev_hash)
    
    print 'Villa analysis is completed.'
    return old_changed_methods, old_improved_changed_methods, old_added_changed_methods, \
        old_all_methods, \
        old_inner_dataflow_methods, old_outer_dataflow_methods, \
        old_l2m, old_m2l, \
        new_changed_methods, new_improved_changed_methods, new_removed_changed_methods, \
        new_all_methods, \
        new_inner_dataflow_methods, new_outer_dataflow_methods, \
        new_l2m, new_m2l, \
        old_changed_tests, new_changed_tests
#         list(set(old_changed_tests + new_changed_tests))
