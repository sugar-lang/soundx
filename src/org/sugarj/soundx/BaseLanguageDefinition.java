/*
 * Copyright (c) 2015, TU Berlin
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 * - Neither the name of the TU Berlin nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sugarj.soundx;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.jsglr.client.SGLR;
import org.spoofax.jsglr.client.imploder.TreeBuilder;
import org.spoofax.terms.StrategoAppl;
import org.spoofax.terms.StrategoConstructor;
import org.spoofax.terms.StrategoList;
import org.spoofax.terms.StrategoString;
import org.spoofax.terms.TermFactory;
import org.strategoxt.HybridInterpreter;
import org.strategoxt.lang.Context;
import org.strategoxt.lang.compat.CompatLibrary;
import org.strategoxt.tools.pp_pp_table_0_0;
import org.strategoxt.tools.ppgenerate_0_0;
import org.sugarj.AbstractBaseLanguage;
import org.sugarj.SXBldLanguage;
import org.sugarj.common.ATermCommands;
import org.sugarj.common.Environment;
import org.sugarj.common.FileCommands;
import org.sugarj.common.StringCommands;
import org.sugarj.cleardep.stamp.Stamper;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.RelativePath;
import org.sugarj.common.path.Path;
import org.sugarj.driver.Driver;
import org.sugarj.driver.DriverParameters;
import org.sugarj.driver.Result;
import org.sugarj.driver.Result.CompilerMode;
import org.sugarj.driver.SDFCommands;
import org.sugarj.stdlib.StdLib;
import org.sugarj.util.Pair;

/**
 * Generation of Sugar* files from a SoundX base language definition.
 *
 * @author Florian Lorenzen <florian.lorenzen@tu-berlin.de>
 */
/**
 * @author florenz
 *
 */
public class BaseLanguageDefinition {
	public BaseLanguageDefinition() {
		interp = new HybridInterpreter();
	}

	/** File extension of a file in the extended language. */
	private String extFileExt;

	/** File extension of a file in the base language. */
	private String baseFileExt;

	/** Name of the nonterminal for toplevel declarations. */
	private String toplevelDeclarationNonterminal;

	/**
	 * Name of the constructor of a namespace declaration and the argument
	 * number of the namespace identifier.
	 */
	private Pair<String, Integer> namespaceDecCons;

	/** Flat, nested, or prefixed namespace. */
	private SXNamespaceKind namespaceKind;

	/**
	 * Constructor names and argument indices of the namespace suffices for
	 * prefixed namespaces.
	 */
	private Map<String, Integer> namespaceSuffices = new HashMap<String, Integer>();

	/**
	 * Names of the constructors of import declarations and the argument number
	 * of the name referred to.
	 */
	private Map<String, Integer> importDecCons = new HashMap<String, Integer>();

	/** Names of the constructors of body declarations. */
	private Set<String> bodyDecCons = new HashSet<String>();

	/** Name of the base language. */
	private String baseLanguageName;

	/** Language library. */
	private SoundXBaseLanguage blInstance;

	/** Stratego interpreter for pretty printing etc. */
	private HybridInterpreter interp;

	/** File name of imported SoundX Stratego file. */
	private final String soundXStrFileName = "org/sugarj/soundx/SoundX.str";

	/** File name of imported SoundX SDF2 file. */
	private final String soundXSdfFileName = "org/sugarj/soundx/SoundX.sdf";

	/** Module name of imported SoundX module. */
	private final String soundXModuleName = "org/sugarj/soundx/SoundX";

	/** bin-directory of plugin. */
	private Path binDir;

	/** src-directory of plguin. */
	private Path srcDir;

	/** Path of sxbld file. */
	private RelativePath bldPath;

	/** Path of generated SDF2 code. */
	private Path sdfPath;

	/** Path of generated Stratego code. */
	private Path strPath;

	/** Path of generated editor services code. */
	private Path servPath;

	/** Path of generated def file. */
	private Path defPath;

	/** Path of generated pretty printer table. */
	private Path ppPath;

	/**
	 * Processes a base language definition.
	 * 
	 * @param bldFilename
	 *            Base language definition to process
	 * @param pluginDirectory
	 *            Directory of the base language plugin
	 */
	public void process(SoundXBaseLanguage language, String bldFilename,
			Path pluginDirectory) {
		blInstance = language;
		setBinDirFromPluginDirectory(pluginDirectory);
		setSrcDirFromPluginDirectory(pluginDirectory);
		bldPath = new RelativePath(bldFilename);
		bldPath.setBasePath(srcDir);
		setBaseLanguageName();
		setGeneratedFilePaths();

		if (generatedFilesOutdated()) {
			runCompiler();
			postProcess(); // also extracts declarations from Stratego file
			generateDefFile();
			generatePPTable();
		} else {
			IStrategoTerm strTerm = parseStratego();
			extractDeclarations(strTerm);
		}
		blInstance.ensureFile(soundXStrFileName);
		blInstance.ensureFile(soundXSdfFileName);
		initSoundXBaseLanguage();
	}

	/** Generate the def file from the sdf file. */
	private void generateDefFile() {
		try {
			FileCommands.writeToFile(defPath, "definition\n\n");
			String sdfText = FileCommands.readFileAsString(sdfPath);
			FileCommands.appendToFile(defPath, sdfText);
		} catch (Exception e) {
			externalFail("write generated def file", e);
		}
	}

	/**
	 * Copies the declarations extracted from the base language definition to
	 * the language library.
	 */
	private void initSoundXBaseLanguage() {
		blInstance.setBaseFileExtension(baseFileExt);
		blInstance.setSugarFileExtension(extFileExt);
		blInstance.setLanguageName(baseLanguageName);
		blInstance.setInitEditor(servPath);
		blInstance.setInitGrammar(sdfPath);
		blInstance.setInitTrans(strPath);
		blInstance.setPackagedGrammar(defPath);
		blInstance.setPpTable(ppPath);
		blInstance.setImportDecCons(importDecCons);
		blInstance.setBodyDecCons(bodyDecCons);
		blInstance.setNamespaceDecCons(namespaceDecCons);
		blInstance.setNamespaceKind(namespaceKind);
		blInstance.setNamespaceSuffices(namespaceSuffices);
	}

	/**
	 * Sets the paths for the generated files in the bin directory of the base
	 * language plugin.
	 */
	private void setGeneratedFilePaths() {
		String sdfFileName = baseLanguageName + ".sdf";
		String strFileName = baseLanguageName + ".str";
		String servFileName = baseLanguageName + ".serv";
		String defFileName = baseLanguageName + ".def";
		String ppFileName = baseLanguageName + ".pp";
		sdfPath = new AbsolutePath(binDir.getAbsolutePath() + File.separator
				+ sdfFileName);
		strPath = new AbsolutePath(binDir.getAbsolutePath() + File.separator
				+ strFileName);
		servPath = new AbsolutePath(binDir.getAbsolutePath() + File.separator
				+ servFileName);
		defPath = new AbsolutePath(binDir.getAbsolutePath() + File.separator
				+ defFileName);
		ppPath = new AbsolutePath(binDir.getAbsolutePath() + File.separator
				+ ppFileName);
	}

	/**
	 * Set base language name from file name of base language definition.
	 */
	private void setBaseLanguageName() {
		baseLanguageName = FileCommands.fileName(bldPath);
	}

	/**
	 * Post processes Sugar* generated sdf and str file.
	 */
	private void postProcess() {
		postProcessStratego();
		postProcessSdf();
	}

	/**
	 * Post process Sugar* generated str file. The imports are replaced by the
	 * single import of the SoundX module. The declarations like the constructor
	 * names for the language processor are extracted and stored.
	 */
	private void postProcessStratego() {
		IStrategoTerm strTerm = parseStratego();
		extractDeclarations(strTerm);
		IStrategoTerm strTermImportsFixed = fixStrategoImports(strTerm);
		String strString = ppStratego(strTermImportsFixed);
		try {
			FileCommands.writeToFile(strPath, strString);
		} catch (Exception e) {
			externalFail("write post processed Stratego code", e);
		}
	}

	/**
	 * Replaces the imports by the import of the SoundX module.
	 * 
	 * @param term
	 *            the STratego syntax tree
	 * @return the Stratego syntax tree with imports replaces
	 */
	private IStrategoTerm fixStrategoImports(IStrategoTerm term) {
		IStrategoTerm header = term.getSubterm(0);
		IStrategoList decls = null;
		if (term.getSubterm(1) instanceof IStrategoList)
			decls = (IStrategoList) term.getSubterm(1);
		else
			fail("Second argument of Stratego module not a list");
		LinkedList<IStrategoTerm> declsNoImports = new LinkedList<IStrategoTerm>();

		for (IStrategoTerm decl : decls) {
			if (decl.getTermType() == IStrategoTerm.APPL) {
				if (!((StrategoAppl) decl).getConstructor().getName()
						.equals("Imports"))
					declsNoImports.addLast(decl);
			} else
				declsNoImports.addLast(decl);
		}

		declsNoImports.addFirst(ATermCommands
				.atermFromString("Imports([Import(\"" + soundXModuleName
						+ "\")])"));
		StrategoList declsTerm = TermFactory.EMPTY_LIST;
		for (Iterator<IStrategoTerm> i = declsNoImports.descendingIterator(); i
				.hasNext();) {
			IStrategoTerm declTerm = i.next();
			declsTerm = new StrategoList(declTerm, declsTerm,
					TermFactory.EMPTY_LIST, decls.getStorageType());
		}

		IStrategoTerm trm = new StrategoAppl(new StrategoConstructor("Module",
				2), new IStrategoTerm[] { header, declsTerm },
				term.getAnnotations(), term.getStorageType());

		return ATermCommands.atermFromString(trm.toString());
		// without the to-and-from String the pretty printer crashes
	}

	/**
	 * Extracts declarations for the language processor and library from base
	 * language definition.
	 * 
	 * @param term
	 *            The ATerm of the Stratego module generated by Sugar*
	 */
	private void extractDeclarations(IStrategoTerm term) {
		IStrategoList decls = (IStrategoList) term.getSubterm(1);
		for (IStrategoTerm decl : decls) {
			if (ATermCommands.isApplication(decl, "Strategies")) {
				StrategoList defs = (StrategoList) decl.getSubterm(0);
				IStrategoTerm head = defs.head();
				if (ATermCommands.isApplication(head, "SDefNoArgs")) {
					String name = ((StrategoString) head.getSubterm(0))
							.getName();
					IStrategoTerm rhs = head.getSubterm(1);
					if (name.equals("sx-ToplevelDeclaration")) {
						// rhs = Build(NoAnnoList(Str("\"ToplevelDec\"")))
						toplevelDeclarationNonterminal = unquote(((StrategoString) rhs
								.getSubterm(0).getSubterm(0).getSubterm(0))
								.getName());
					} else if (name.equals("sx-extensible-file-ext")) {
						extFileExt = unquote(((StrategoString) rhs
								.getSubterm(0).getSubterm(0).getSubterm(0))
								.getName());
					} else if (name.equals("sx-base-file-ext")) {
						baseFileExt = unquote(((StrategoString) rhs
								.getSubterm(0).getSubterm(0).getSubterm(0))
								.getName());
					} else if (name.equals("sx-body-decs")) {
						extractBodyDecsDeclaration(rhs);
					} else if (name.equals("sx-namespace-dec")) {
						extractNamespaceDecDeclaration(rhs);
					} else if (name.equals("sx-import-decs")) {
						extractImportDecsDeclaration(rhs);
					} else if (name.equals("sx-namespace-kind")) {
						extractNamespaceKindDeclaration(rhs);
					} else if (name.equals("sx-namespace-suffices")) {
						extractNamespaceSufficesDeclaration(rhs);
					}
				}
			}
		}
	}

	/**
	 * Helper for extractDeclarations to extract sx-namespace-suffices.
	 *
	 * @param rhs
	 *            right-hand side of sx-namespace-suffices
	 */
	private void extractNamespaceSufficesDeclaration(IStrategoTerm rhs) {
		// rhs =
		// Build(NoAnnoList(ListTail([NoAnnoList(Tuple([NoAnnoList(Str("\"SXCons7\"")),NoAnnoList(Int("2"))]))],NoAnnoList(List([NoAnnoList(Tuple([NoAnnoList(Str("\"SXCons6\"")),NoAnnoList(Int("2"))]))]))
		IStrategoTerm current = rhs.getSubterm(0);
		while (current != null) {
			// unwrap NoAnnoList
			StrategoAppl listCons = (StrategoAppl) current.getSubterm(0);
			String applConsName = listCons.getConstructor().getName();
			if (applConsName.equals("List")) {
				StrategoList elems = (StrategoList) listCons.getSubterm(0);
				if (elems.size() == 0) {
					current = null;
				} else if (elems.size() == 1) {
					IStrategoTerm elem = elems.head();
					StrategoList comps = (StrategoList) elem.getSubterm(0)
							.getSubterm(0);
					IStrategoTerm fstComp = comps.head();
					IStrategoTerm sndComp = comps.tail().head();
					String consName = unquote(((StrategoString) fstComp
							.getSubterm(0).getSubterm(0)).getName());
					Integer index = Integer.valueOf(((StrategoString) sndComp
							.getSubterm(0).getSubterm(0)).getName());
					namespaceSuffices.put(consName, index);
					current = null;
				} else
					fail("Error reading end of namespace-suffices list");
			} else if (applConsName.equals("ListTail")) {
				StrategoList hd = (StrategoList) listCons.getSubterm(0);
				IStrategoTerm elem = hd.head();
				StrategoList comps = (StrategoList) elem.getSubterm(0)
						.getSubterm(0);
				IStrategoTerm fstComp = comps.head();
				IStrategoTerm sndComp = comps.tail().head();
				String consName = unquote(((StrategoString) fstComp.getSubterm(
						0).getSubterm(0)).getName());
				Integer index = Integer.valueOf(((StrategoString) sndComp
						.getSubterm(0).getSubterm(0)).getName());
				namespaceSuffices.put(consName, index);
				current = listCons.getSubterm(1);
			} else
				fail("Error reading namespace-suffices");
		}
	}

	/**
	 * Helper for extractDeclarations to extract sx-namespace-kind.
	 *
	 * @param rhs
	 *            right-hand side of sx-namespace-kind
	 */
	private void extractNamespaceKindDeclaration(IStrategoTerm rhs) {
		// rhs =
		// Build(NoAnnoList(Op("SXNamespaceNested",[NoAnnoList(Str("\".\""))])))
		// strip Build and NoAnnoList
		IStrategoTerm rhs1 = rhs.getSubterm(0).getSubterm(0);
		String kind = unquote(rhs1.getSubterm(0).toString());
		if (kind.equals("SXNamespaceFlat")) {
			namespaceKind = new SXNamespaceFlat();
		} else if (kind.equals("SXNamespaceNested")) {
			String sepQuoted = ((IStrategoList) rhs1.getSubterm(1)).head()
					.getSubterm(0).getSubterm(0).toString();
			char sep = sepQuoted.substring(3, 4).charAt(0);
			namespaceKind = new SXNamespaceNested(sep);
		} else if (kind.equals("SXNamespacePrefixed")) {
			String sepQuoted = ((IStrategoList) rhs1.getSubterm(1)).head()
					.getSubterm(0).getSubterm(0).toString();
			char sep = sepQuoted.substring(3, 4).charAt(0);
			namespaceKind = new SXNamespacePrefixed(sep);
		}
	}

	/**
	 * Helper for extractDeclarations to extract sx-import-decs.
	 *
	 * @param rhs
	 *            right-hand side of sx-import-decs
	 */
	private void extractImportDecsDeclaration(IStrategoTerm rhs) {
		// rhs =
		// Build(NoAnnoList(ListTail([NoAnnoList(Tuple([NoAnnoList(Str("\"SXCons7\"")),NoAnnoList(Int("2"))]))],NoAnnoList(List([NoAnnoList(Tuple([NoAnnoList(Str("\"SXCons6\"")),NoAnnoList(Int("2"))]))]))
		IStrategoTerm current = rhs.getSubterm(0);
		while (current != null) {
			// unwrap NoAnnoList
			StrategoAppl listCons = (StrategoAppl) current.getSubterm(0);
			String applConsName = listCons.getConstructor().getName();
			if (applConsName.equals("List")) {
				StrategoList elems = (StrategoList) listCons.getSubterm(0);
				if (elems.size() == 0) {
					current = null;
				} else if (elems.size() == 1) {
					IStrategoTerm elem = elems.head();
					StrategoList comps = (StrategoList) elem.getSubterm(0)
							.getSubterm(0);
					IStrategoTerm fstComp = comps.head();
					IStrategoTerm sndComp = comps.tail().head();
					String consName = unquote(((StrategoString) fstComp
							.getSubterm(0).getSubterm(0)).getName());
					Integer index = Integer.valueOf(((StrategoString) sndComp
							.getSubterm(0).getSubterm(0)).getName());
					importDecCons.put(consName, index);
					current = null;
				} else
					fail("Error reading end of import-decs list");
			} else if (applConsName.equals("ListTail")) {
				StrategoList hd = (StrategoList) listCons.getSubterm(0);
				IStrategoTerm elem = hd.head();
				StrategoList comps = (StrategoList) elem.getSubterm(0)
						.getSubterm(0);
				IStrategoTerm fstComp = comps.head();
				IStrategoTerm sndComp = comps.tail().head();
				String consName = unquote(((StrategoString) fstComp.getSubterm(
						0).getSubterm(0)).getName());
				Integer index = Integer.valueOf(((StrategoString) sndComp
						.getSubterm(0).getSubterm(0)).getName());
				importDecCons.put(consName, index);
				current = listCons.getSubterm(1);
			} else
				fail("Error reading import-decs");
		}
	}

	/**
	 * Helper for extractDeclarations to extract sx-namespace-dec.
	 *
	 * @param rhs
	 *            right-hand side of sx-namespace-dec
	 */
	private void extractNamespaceDecDeclaration(IStrategoTerm rhs) {
		// rhs =
		// Build(NoAnnoList(Tuple([NoAnnoList(Str("\"SXCons5\"")),NoAnnoList(Int("2"))]))))])
		StrategoList tupleComps = (StrategoList) rhs.getSubterm(0)
				.getSubterm(0).getSubterm(0);
		IStrategoTerm fstComp = tupleComps.head();
		IStrategoTerm sndComp = tupleComps.tail().head();
		String consName = unquote(((StrategoString) fstComp.getSubterm(0)
				.getSubterm(0)).getName());
		Integer index = Integer.valueOf(((StrategoString) sndComp.getSubterm(0)
				.getSubterm(0)).getName());
		namespaceDecCons = new Pair<String, Integer>(consName, index);
	}

	/**
	 * Helper for extractDeclarations to extract sx-body-decs.
	 *
	 * @param rhs
	 *            right-hand side of sx-namespacebody-decs
	 */
	private void extractBodyDecsDeclaration(IStrategoTerm rhs) {
		// rhs =
		// Build(NoAnnoList(List([NoAnnoList(Str("\"SXCons10\""))])))
		IStrategoTerm current = rhs.getSubterm(0);

		while (current != null) {
			// unwrap NoAnnoList
			StrategoAppl listCons = (StrategoAppl) current.getSubterm(0);
			String applConsName = listCons.getConstructor().getName();
			if (applConsName.equals("List")) {
				StrategoList elems = (StrategoList) listCons.getSubterm(0);
				if (elems.size() == 0) {
					current = null;
				} else if (elems.size() == 1) {
					IStrategoTerm elem = elems.head();
					String consName = unquote(((StrategoString) elem
							.getSubterm(0).getSubterm(0)).getName());
					bodyDecCons.add(consName);
					current = null;
				} else
					fail("Error reading end of body-decs list");
			} else if (applConsName.equals("ListTail")) {
				StrategoList hd = (StrategoList) listCons.getSubterm(0);
				IStrategoTerm elem = hd.head();
				String consName = unquote(((StrategoString) elem.getSubterm(0)
						.getSubterm(0)).getName());
				bodyDecCons.add(consName);

				current = listCons.getSubterm(1);
			} else
				fail("Error reading body-decs");
		}
	}

	/**
	 * Removes a leading and a trailing double quote.
	 * 
	 * @param text
	 *            test possibly quoted
	 * @return the text without quotes
	 */
	private String unquote(String text) {
		String out = text;
		if (out.charAt(0) == '"')
			out = out.substring(1);
		if (out.charAt(out.length() - 1) == '"')
			out = out.substring(0, out.length() - 1);
		return out;
	}

	/**
	 * Generates pretty print table from base language grammar.
	 */
	private void generatePPTable() {
		Context ctx = interp.getCompiledContext();

		ctx.addOperatorRegistry(new CompatLibrary());
		// necessary because SSL_EXT_topdown_fput is not defined otherwise

		IStrategoTerm sdfTerm = parseSdf();
		try {
			IStrategoTerm sdfTermFixed = ATermCommands.fixSDF(sdfTerm, interp);
			// without fixSDF, ppgenerate does not recognize the attributes

			IStrategoTerm result = ppgenerate_0_0.instance.invoke(ctx,
					sdfTermFixed);
			String table = ((StrategoString) pp_pp_table_0_0.instance.invoke(
					ctx, result)).getName();
			FileCommands.writeToFile(ppPath, table);
		} catch (Exception e) {
			externalFail("generating and writing the pretty printer table", e);
		}
	}

	/**
	 * Post process SDF. Replace the imports by single import of SoundX module
	 * and add the productions for ToplevelDeclaration.
	 */
	private void postProcessSdf() {
		IStrategoTerm sdfTerm = parseSdf();
		IStrategoTerm sdfTermNoImports = fixSdfImports(sdfTerm);
		IStrategoTerm sdfTermWithToplevelDec = fixSdfToplevelDec(sdfTermNoImports);
		IStrategoTerm sdfTermFixed = null;
		try {
			sdfTermFixed = ATermCommands.fixSDF(sdfTermWithToplevelDec, interp);
			// Without fixSDF the attributes are pretty printed wrongly
		} catch (Exception e) {
			externalFail("fixing the attributes of the post process SDF code",
					e);
		}
		String sdfString = ppSdf(sdfTermFixed);
		try {
			FileCommands.writeToFile(sdfPath, sdfString);
		} catch (Exception e) {
			externalFail("writing the post processed SDF code", e);
		}
	}

	/**
	 * Adds a production for ToplevelDeclaration. The left-hand side of the
	 * production is read from the base language definition.
	 * 
	 * @param term
	 *            the SDF syntax tree
	 * @return the SDF syntax tree with production added
	 */
	private IStrategoTerm fixSdfToplevelDec(IStrategoTerm term) {
		IStrategoTerm header = term.getSubterm(0);
		IStrategoTerm imports = term.getSubterm(1);
		StrategoList body = (StrategoList) term.getSubterm(2);
		String cfSection = "exports(context-free-syntax([prod([sort(\""
				+ baseLanguageName + "Gnd" + toplevelDeclarationNonterminal
				+ "\")], sort(\"ToplevelDeclaration\"), no-attrs())]))";
		IStrategoTerm cf = ATermCommands.atermFromString(cfSection);
		IStrategoTerm newBody = new StrategoList(cf, body,
				TermFactory.EMPTY_LIST, body.getStorageType());
		return new StrategoAppl(new StrategoConstructor("module", 3),
				new IStrategoTerm[] { header, imports, newBody },
				term.getAnnotations(), term.getStorageType());
	}

	/**
	 * Replace imports with import of SoundX module.
	 * 
	 * @param term
	 *            the SDF syntax tree
	 * @return the SDF syntax tree with imports replaced
	 */
	private IStrategoTerm fixSdfImports(IStrategoTerm term) {
		IStrategoTerm header = term.getSubterm(0);
		IStrategoTerm body = term.getSubterm(2);
		IStrategoTerm common = ATermCommands
				.atermFromString("imports(module(unparameterized(\""
						+ soundXModuleName + "\")))");
		IStrategoTerm imports = new StrategoList(common,
				TermFactory.EMPTY_LIST, TermFactory.EMPTY_LIST,
				term.getStorageType());
		return new StrategoAppl(new StrategoConstructor("module", 3),
				new IStrategoTerm[] { header, imports, body },
				term.getAnnotations(), term.getStorageType());
	}

	/**
	 * Parses the Sugar* generated sdf file.
	 * 
	 * @return the syntax tree of the sdf file
	 */
	private IStrategoTerm parseSdf() {
		Path sdfTbl = blInstance.ensureFile("org/sugarj/languages/Sdf2.tbl");
		return parse(sdfTbl, sdfPath, "Sdf2Module");
	}

	/**
	 * Parses the Sugar* generated str file.
	 * 
	 * @return the syntax tree of the str file
	 */
	private IStrategoTerm parseStratego() {
		Path strTbl = blInstance
				.ensureFile("org/sugarj/languages/Stratego.tbl");
		return parse(strTbl, strPath, "StrategoModule");
	}

	/**
	 * Parses the given input with SGLR.
	 * 
	 * @param table
	 *            parse table to use
	 * @param inputFile
	 *            name of the input file
	 * @param startSymbol
	 *            name of the start-symbol for parsing
	 * @return
	 */
	private IStrategoTerm parse(Path table, Path inputFile, String startSymbol) {
		SGLR parser = null;
		try {
			parser = new SGLR(new TreeBuilder(),
					ATermCommands.parseTableManager.loadFromFile(table
							.getAbsolutePath()));
			parser.setUseStructureRecovery(true);
		} catch (Exception e) {
			externalFail(
					"setting up a parser for the table "
							+ table.getAbsolutePath(), e);
		}

		Object parseResult = null;

		try {
			String fileContent = FileCommands.readFileAsString(inputFile);
			parseResult = parser.parseMax(fileContent,
					sdfPath.getAbsolutePath(), startSymbol);
		} catch (Exception e) {
			externalFail("parsing the file " + inputFile.getAbsolutePath(), e);
		}
		Object[] os = (Object[]) parseResult;
		IStrategoTerm term = (IStrategoTerm) os[0];
		return term;
	}

	/**
	 * Pretty prints an SDF syntax tree.
	 * 
	 * @param term
	 *            the SDF syntax tree
	 * @return pretty printed SDF code
	 */
	private String ppSdf(IStrategoTerm term) {
		String result = null;
		try {
			result = SDFCommands.prettyPrintSDF(term, interp);
		} catch (Exception e) {
			externalFail("pretty printing an SDF syntax tree", e);
		}
		return result;
	}

	/**
	 * Pretty prints a Stratego syntax tree.
	 * 
	 * @param term
	 *            the Stratego syntax tree
	 * @return pretty printed Stratego code
	 */
	private String ppStratego(IStrategoTerm term) {
		String result = null;
		try {
			result = SDFCommands.prettyPrintSTR(term, interp);
		} catch (Exception e) {
			externalFail("pretty printing a Stratego syntax tree", e);
		}
		return result;
	}

	/**
	 * Checks if the generated files are older than the base language definition
	 * file.
	 * 
	 * @return true if generated files are aoutdated, false otherwise
	 */
	private boolean generatedFilesOutdated() {
		if (FileCommands.fileExists(sdfPath)
				&& FileCommands.fileExists(strPath)
				&& FileCommands.fileExists(defPath)
				&& FileCommands.fileExists(servPath)
				&& FileCommands.fileExists(ppPath)) {
			return !(FileCommands.isModifiedLater(sdfPath, bldPath)
					&& FileCommands.isModifiedLater(strPath, bldPath)
					&& FileCommands.isModifiedLater(defPath, bldPath)
					&& FileCommands.isModifiedLater(servPath, bldPath) && FileCommands
						.isModifiedLater(ppPath, bldPath));
		} else
			return true;
	}

	/**
	 * Runs the Sugar* compiler with the sxbld language to generate the str and
	 * sdf file from the base language definition.
	 */
	private void runCompiler() {
		IProgressMonitor monitor = new NullProgressMonitor();
		AbstractBaseLanguage baseLang = SXBldLanguage.getInstance();
		Environment environment = new Environment(StdLib.stdLibDir,
				Stamper.DEFAULT);

		// Create a fresh cache directory. Otherwise, Sugar* will only reread
		// the SXBld implementation when the version changes.
		String tmpdir = null;
		try {
			tmpdir = Files.createTempDirectory("sugarj-soundx",
					new FileAttribute[] {}).toString();
		} catch (Exception e) {
			externalFail("creating a temporary directory", e);
		}
		RelativePath cacheDir = new RelativePath(new AbsolutePath(tmpdir),
				"sugarjcache");

		environment.setCacheDir(cacheDir);
		environment.setAtomicImportParsing(false);
		environment.setNoChecking(false);

		environment.setMode(new CompilerMode(binDir, false));
		Result result = null;
		try {
			result = Driver.run(DriverParameters.create(environment, baseLang,
					bldPath, monitor));
			// TODO generate proper editor services file here
			String editorServicesHeader = "module " + baseLanguageName + "\n";
			FileCommands.writeToFile(servPath, editorServicesHeader);
		} catch (Exception e) {
			externalFail("writing the editor services file", e);
		}
		// check for errors processing the base language definition
		List<String> errors = result.getCollectedErrors();
		if (errors.size() > 0) {
			String messages = StringCommands.printListSeparated(errors, "\n");
			processingError(messages);
		}
	}

	/**
	 * Sets pluginDirectory/bin.
	 * 
	 * @param pluginDirectory
	 */
	private void setBinDirFromPluginDirectory(Path pluginDirectory) {
		binDir = new AbsolutePath(pluginDirectory.getAbsolutePath()
				+ File.separator + "bin");
	}

	/**
	 * Sets pluginDirectory/src.
	 * 
	 * @param pluginDirectory
	 */
	private void setSrcDirFromPluginDirectory(Path pluginDirectory) {
		srcDir = new AbsolutePath(pluginDirectory.getAbsolutePath()
				+ File.separator + "src");
	}

	/**
	 * Signal some error indicating a bug in the SoundX implementation
	 * 
	 * @param desc
	 *            description of the error
	 */
	private void fail(String desc) {
		throw new RuntimeException(
				"A bug in the SoundX implementation occured:\n" + desc);
	}

	/**
	 * Signals an externally caused error, like an I/O error.
	 * 
	 * @param desc
	 *            description what SoundX was trying to do
	 * @param e
	 *            the exception encountered
	 */
	private void externalFail(String desc, Exception e) {
		LinkedList<StackTraceElement> trace = new LinkedList<StackTraceElement>();
		for (StackTraceElement elem : e.getStackTrace())
			trace.add(elem);
		String message = "SoundX base language processing failed unexpectedly.\n"
				+ "While trying to "
				+ desc
				+ ", the following exception occured:\n"
				+ e.toString()
				+ "\n"
				+ "The stack trace leading to this problem is:\n"
				+ StringCommands.printListSeparated(trace, "\n");

		throw new RuntimeException(message);
	}

	/**
	 * Signal an error due to mistakes in the base language definition.
	 * 
	 * @param desc
	 *            description of the error
	 */
	private void processingError(String desc) {
		String message = "While processing the base language description for "
				+ baseLanguageName + "\n" + "the following error occured:\n"
				+ desc;
		throw new RuntimeException(message);
	}
}
