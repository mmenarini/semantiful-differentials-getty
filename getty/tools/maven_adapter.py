import mvn, os, git_adapter

def get_bin_path(hash):
    git_adapter.checkout(hash)
    maven_clean()
    return mvn.path_from_mvn_call("outputDirectory")

def get_test_bin_path(hash):
    git_adapter.checkout(hash)
    maven_clean()
    return mvn.path_from_mvn_call("testOutputDirectory")

def get_source_directory(hash):
    git_adapter.checkout(hash)
    maven_clean()
    return mvn.path_from_mvn_call("sourceDirectory")

def get_test_source_directory(hash):
    git_adapter.checkout(hash)
    maven_clean()
    return mvn.path_from_mvn_call("testSourceDirectory")

def get_full_class_path(hash, junit_path, sys_classpath, bin_output, test_output):
    git_adapter.checkout(hash)
    maven_clean()
    return mvn.full_classpath(junit_path, sys_classpath, bin_output, test_output)

def compile_tests(hash):
    git_adapter.checkout(hash)
    maven_clean()
    os.sys_call("mvn test-compile")

def get_junit_torun(cust_mvn_repo, hash):
    git_adapter.checkout(hash)
    maven_clean()
    return mvn.junit_torun_str(cust_mvn_repo)

def maven_clean():
    os.sys_call("mvn clean")

def generate_test_report(go, hash):
    git_adapter.checkout(hash)
    maven_clean()
    mvn.generate_coverage_report(go, hash)