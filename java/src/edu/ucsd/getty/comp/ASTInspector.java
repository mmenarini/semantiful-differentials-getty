package edu.ucsd.getty.comp;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import edu.ucsd.getty.IMethodRecognizer;
import edu.ucsd.getty.visitors.MethodDeclarationVisitor;
import edu.ucsd.getty.visitors.MethodLineNumberSrcVisitor;

public class ASTInspector implements IMethodRecognizer {
	
	Map<String, String> fileline2method = new HashMap<String, String>();
	Map<String, Set<String>> method2filelines = new HashMap<String, Set<String>>();
	
	@Override
	public Map<String, String> l2m() {
		return this.fileline2method;
	}
	
	@Override
	public Map<String, Set<String>> m2l() {
		return this.method2filelines;
	}

	@Override
	public Set<String> changedMethods(String targetFolder, Map<String, Integer[]> diffs) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Set<String> changedMethods(Map<String, Integer[]> diffs) {
		// each diff is a pair of source file and its list of line numbers changed
		Set<String> allChanged = new HashSet<String>();
		for (String source : diffs.keySet()) {
			try {
				FileInputStream in = new FileInputStream(source);
				CompilationUnit cu = JavaParser.parse(in);
				in.close();
				
				Integer[] lines = diffs.get(source);
				for (int line : lines) {
					String possibleMethod = this.changedMethod(cu, line);
					if (possibleMethod != null) {
						allChanged.add(possibleMethod);
						updatelm(source, line, possibleMethod);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(21);
			}
		}
		return allChanged;
	}
	
	private void updatelm(String source, int line, String method) {
		String fileloc = source+","+line;
		if (!this.fileline2method.containsKey(fileloc))
			this.fileline2method.put(fileloc, method);
		if (!this.method2filelines.containsKey(method))
			this.method2filelines.put(method, new HashSet<String>());
		this.method2filelines.get(method).add(fileloc);
	}
	
	protected String changedMethod(CompilationUnit cu, int lineNumber) {
		try {
			String qualifiedMethodName = null;
			
			MethodLineNumberSrcVisitor visitor = new MethodLineNumberSrcVisitor();
			Node visited = visitor.visit(cu, lineNumber);
			if (visited == null)
				return null;
			
			String resultClassName = visited.getClass().getName();
			if (resultClassName.equals("com.github.javaparser.ast.body.ConstructorDeclaration")) {
				ConstructorDeclaration decl = (ConstructorDeclaration) visited;
				qualifiedMethodName = getQualifiedConstructorName(decl) + "-" + decl.getBeginLine();
			} else if (resultClassName.equals("com.github.javaparser.ast.body.MethodDeclaration")) {
				MethodDeclaration decl = (MethodDeclaration) visited;
				qualifiedMethodName = getQualifiedMethodName(decl) + "-" + decl.getBeginLine();
			} else {
				System.out.println("unprocesed method type: " + resultClassName);
			}
			return qualifiedMethodName;
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(21);
			return null;
		}
	}
	
	static public String getQualifiedConstructorName(ConstructorDeclaration cons) throws Exception {
		String constructorName = cons.getName();
		Node parent = cons.getParentNode();
		String separator = null;
		while (parent != null) {
			String className = parent.getClass().getName();
			if (className.equals("com.github.javaparser.ast.body.ClassOrInterfaceDeclaration")) {
				String currentClassName = ((ClassOrInterfaceDeclaration) parent).getName();
				if (separator == null) {
					separator = ":";
					if (!constructorName.equals(currentClassName))
						throw new Exception("current class constructor name " + constructorName 
								+ " should be equal to class name: " + currentClassName);
					constructorName = currentClassName + separator + "<init>";
				} else if (separator.equals(":")) {
					separator = "$";
					constructorName = currentClassName + separator + constructorName;
				} else if (separator.equals("$")) {
					constructorName = currentClassName + separator + constructorName;
				} else
					throw new Exception("[C] unexpected previous separator " + separator 
							+ " when current parent is a class " + currentClassName
							+ " and current method name is " + constructorName);
			}
			else if (className.equals("com.github.javaparser.ast.body.EnumDeclaration")) {
				String currentEnumName = ((EnumDeclaration) parent).getName();
				if (separator == null) {
					separator = ":";
					if (!constructorName.equals(currentEnumName))
						throw new Exception("current enum constructor name " + constructorName 
								+ " should be equal to enum name: " + currentEnumName);
					constructorName = currentEnumName + separator + "<init>";
				} else if (separator.equals(":")) {
					separator = "$";
					constructorName = currentEnumName + separator + constructorName;
				} else if (separator.equals("$")) {
					constructorName = currentEnumName + separator + constructorName;
				} else
					throw new Exception("[C] unexpected previous separator " + separator 
							+ " when current parent is an enum " + currentEnumName
							+ " and current method name is " + constructorName);
//				System.out.println("[C] RARE: process enum type and get (maybe intermediate): " + constructorName);
			}
			else if (className.equals("com.github.javaparser.ast.CompilationUnit")) {
				PackageDeclaration pkg = ((CompilationUnit) parent).getPackage();
				if (pkg != null)
					constructorName = pkg.getName().toString() + "." + constructorName;
			} else {
//				System.out.println("[C] unprocessed type: " + className);
				throw new Exception("[C] unprocessed type: " + className);
			}
			parent = parent.getParentNode();
		}
		return constructorName;
	}
	
	static public String getQualifiedMethodName(MethodDeclaration decl) throws Exception {
		String methodName = decl.getName();
		Node parent = decl.getParentNode();
		String separator = null;
		while (parent != null) {
			String className = parent.getClass().getName();
			if (className.equals("com.github.javaparser.ast.body.ClassOrInterfaceDeclaration")) {
				if (separator == null)
					separator = ":";
				else if (separator.equals(":"))
					separator = "$";
				else if (separator.equals("$"))
					separator = "$";
				else
					throw new Exception("[M] unexpected previous separator " + separator 
							+ " when current parent is a class " + ((ClassOrInterfaceDeclaration) parent).getName()
							+ " and current method name is " + methodName);
				methodName = ((ClassOrInterfaceDeclaration) parent).getName() + separator + methodName;
			}
			else if (className.equals("com.github.javaparser.ast.body.EnumDeclaration")) {
				if (separator == null)
					separator = ":";
				else if (separator.equals(":"))
					separator = "$";
				else if (separator.equals("$"))
					separator = "$";
				else
					throw new Exception("[M] unexpected previous separator " + separator 
							+ " when current parent is an enum " + ((EnumDeclaration) parent).getName()
							+ " and current method name is " + methodName);
				methodName = ((EnumDeclaration) parent).getName() + separator + methodName;
//				System.out.println("[M] RARE: process enum type and get (maybe intermediate): " + methodName);
			}
			else if (className.equals("com.github.javaparser.ast.CompilationUnit")) {
				PackageDeclaration pkg = ((CompilationUnit) parent).getPackage();
				if (pkg != null) {					
					methodName = pkg.getName().toString() + "." + methodName;
				}
			}
			else {				
//				System.out.println("[M] unprocessed type: " + className);
				throw new Exception("[M] unprocessed type: " + className);
			}
			parent = parent.getParentNode();
		}
		return methodName;
	}
	
	public static Map<String, Integer> getMethodLineNumberMap(String path, String src_postfix) {
		Map<String, Integer> results = new HashMap<String, Integer>();
		if (getPathType(path) == PathType.FILE) {
			MethodDeclarationVisitor visitor = new MethodDeclarationVisitor();
			try {
				if (path.endsWith(src_postfix)) {
					FileInputStream in = new FileInputStream(path);
					CompilationUnit cu = JavaParser.parse(in);
					in.close();
					visitor.visit(cu, results);
				}
			} catch (Exception e) {
				System.out.println("Failed to get method-linenums for path: " + path);
			}
			return results;
		} else if (getPathType(path) == PathType.DIR) {
			File dir = new File(path);
			for (String sub : dir.list()) {
				results.putAll(getMethodLineNumberMap(dir.getPath() + "/" + sub, src_postfix));
			}
			return results;
		} else {
			return results;
		}
	}
	
	private enum PathType { DIR, FILE, ELSE }
	
	private static PathType getPathType(String path) {
		Path p = Paths.get(path);
		if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
			return PathType.DIR;
		else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
			return PathType.FILE;
		else
			return PathType.ELSE;
	}

	public static void main(String[] args) {
		System.out.println("Inspector of method name by AST");
		
		if (args.length > 0) {			
			IMethodRecognizer rec = new ASTInspector();
			Map<String, Integer[]> diffs = new HashMap<String, Integer[]>();
			diffs.put("/Users/yanyan/Projects/studies/implementation_alt/commons-math/src/main/java/org/apache/commons/math3/analysis/function/Gaussian.java", 
					new Integer[]{69, 155});
			Set<String> result = rec.changedMethods(diffs);
			for (String k : result)
				System.out.println(k);
			
			IMethodRecognizer rec3 = new ASTInspector();
			Map<String, Integer[]> diffs3 = new HashMap<String, Integer[]>();
			diffs3.put("/Users/yanyan/Projects/studies/implementation_alt/commons-math/src/main/java/org/apache/commons/math3/analysis/differentiation/DerivativeStructure.java", 
					new Integer[]{632, 1182, 1191});
			Set<String> result3 = rec3.changedMethods(diffs3);
			for (String k : result3)
				System.out.println(k);
		}
	}

}
