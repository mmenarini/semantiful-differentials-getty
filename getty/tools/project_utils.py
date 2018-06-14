class ProjectUtils:
    project_path = ""
    go = ""

    @staticmethod
    def init(project_path, go):
        ProjectUtils.project_path = project_path[:-1]
        ProjectUtils.go = go[:-1]

    @staticmethod
    def get_version_path(commit_hash):
        return ProjectUtils.go + "/" + commit_hash
