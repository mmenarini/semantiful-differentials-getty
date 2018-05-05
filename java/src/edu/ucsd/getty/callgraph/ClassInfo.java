package edu.ucsd.getty.callgraph;

import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.ReferenceType;


public class ClassInfo {

	private final JavaClass self;

	// will be set once when it is initialized
	public final String qualifiedName;
	public final String packageName;

	// will be set when it is initialized, then updated in analyzer, twice
	public Set<String> methods;

	// will be set more in analyzer
	public Set<String> supers;  // super class (maybe more for interface) and iterfaces
	public Set<String> subs;  // sub classes or implementations
	public Set<String> classReferences;

	public ClassInfo(JavaClass self) {
		this.self = self;
		this.classReferences = new HashSet<String>();
		this.qualifiedName = self.getClassName();
		this.packageName = self.getPackageName();
		this.methods = methods2stringSet(self.getMethods());
		/*Megan’s code*/
		Get_Referenced_Classes();
		/**/

		this.supers = superclassNinterfaces();
		this.subs = new HashSet<String>();
	}

	public void Get_Referenced_Classes() {
		ConstantPool cp = this.self.getConstantPool();
		ConstantPoolGen cpg = new ConstantPoolGen(cp);
		for(Method m : this.self.getMethods()){
			MethodGen mg = new MethodGen(m, this.qualifiedName, cpg);
			InstructionList il = mg.getInstructionList();
			if (il == null){
				continue;
			}
			InstructionHandle[] ihs = il.getInstructionHandles();
			for(int i=0; i < ihs.length; i++){
				Instruction instruction = ihs[i].getInstruction();
				if(!(instruction instanceof InvokeInstruction)){
					continue;
				}
				InvokeInstruction ii = (InvokeInstruction)instruction;
				ReferenceType rfType = ii.getReferenceType(cpg);
				if(!(rfType instanceof ObjectType)){
					continue;
				}

				ObjectType oType = (ObjectType) rfType;
				String referencedClassName = oType.getClassName();
				addReferencedClass(referencedClassName);
			}
		}
		//for debugging of class references
//		for (String rclass: this.classReferences){
//			System.out.println("\nValue: " + rclass + "\n");
//		}
	}

	public void addReferencedClass(String referencedClassName) {
		if(!(referencedClassName.equals(this.qualifiedName))) {
			this.classReferences.add(referencedClassName);
		}
	}

	private Set<String> methods2stringSet(Method[] methods) {
		Set<String> result = new HashSet<String>();
		for (int i = 0; i < methods.length; i ++) {
			String methodName = methods[i].getName();
			if (!NameHandler.shallExcludeMethod(methodName))
				result.add(methods[i].getName());
		}
		return result;
	}

	private Set<String> superclassNinterfaces() {
		Set<String> all = new HashSet<String>();
		String superclassname = self.getSuperclassName();
		if (superclassname != self.getClassName()
				&& !NameHandler.shallExcludeClass(superclassname))
			all.add(superclassname);
		for (String interfacename : self.getInterfaceNames())
			if (!NameHandler.shallExcludeClass(interfacename))
				all.add(interfacename);
		return all;
	}

	public JavaClass getJavaClass() {
		return this.self;
	}

	public boolean hasSuper(String superCandidiate) {
		return supers.contains(superCandidiate);
	}

	public boolean hasSub(String subCandidate) {
		return subs.contains(subCandidate);
	}

	public boolean hasMethod(String methodCandidate) {
		return methods.contains(methodCandidate);
	}

}
