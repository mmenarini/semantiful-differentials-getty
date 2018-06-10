import mvn, os

def get_bin_path(hash):
    os.sys_call("git checkout " + hash)
    maven_clean()
    return mvn.path_from_mvn_call("outputDirectory")

def get_test_bin_path(hash):
    os.sys_call("git checkout " + hash)
    maven_clean()
    return mvn.path_from_mvn_call("testOutputDirectory")

def get_source_directory(hash):
    os.sys_call("git checkout " + hash)
    maven_clean()
    return mvn.path_from_mvn_call("sourceDirectory")

def get_test_source_directory(hash):
    os.sys_call("git checkout " + hash)
    maven_clean()
    return mvn.path_from_mvn_call("testSourceDirectory")

def get_full_class_path(hash, junit_path, sys_classpath, bin_output, test_output):
    os.sys_call("git checkout " + hash)
    maven_clean()
    return mvn.full_classpath(junit_path, sys_classpath, bin_output, test_output)

def compile_tests(hash):
    os.sys_call("git checkout " + hash)
    maven_clean()
    os.sys_call("mvn test-compile")

def get_Junit_torun(cust_mvn_repo, hash):
    os.sys_call("git checkout " + hash)
    maven_clean()
    return mvn.junit_torun_str(cust_mvn_repo)

def maven_clean():
    os.sys_call("mvn clean")

def generate_test_report(go, hash):
    os.sys_call("git checkout " + hash)
    maven_clean()
    mvn.generate_coverage_report(go, hash)