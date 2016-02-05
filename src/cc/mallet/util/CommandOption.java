/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
*/

package cc.mallet.util;

import java.util.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import cc.mallet.util.BshInterpreter;


public abstract class CommandOption
{
	static BshInterpreter interpreter;

	/** Maps a Java class to the array of CommandOption objects that are owned by it. */
	static HashMap class2options = new HashMap ();

	Class owner;
	/** The name of the argument, eg "input" */
	java.lang.String name;
	/** The display name of the argument type, eg "[TRUE|FALSE]" or "FILE" */
	java.lang.String argName;
	
	/** The type of the argument, if present */
	Class argType;
	boolean argRequired;
	java.lang.String shortdoc;
	java.lang.String longdoc;
	java.lang.String fullName;
	
	/** did this command option get processed, or do we just have default value */
	boolean invoked = false;

	public CommandOption (Class owner, java.lang.String name, java.lang.String argName,
						  Class argType, boolean argRequired,
						  java.lang.String shortdoc, java.lang.String longdoc)
	{
		this.owner = owner;
		this.name = name;
		this.argName = argName;
		this.argType = argType;
		this.argRequired = argRequired;
		this.shortdoc = shortdoc;
		this.longdoc = longdoc;
		Package p = owner.getPackage();
		this.fullName = (p != null ? p.toString() : "") + name;
		if (interpreter == null)
			interpreter = new BshInterpreter ();
		if (owner != CommandOption.class) {
			CommandOption.List options = (CommandOption.List) class2options.get (owner);
			if (options == null) {
				options = new CommandOption.List ("");
				class2options.put (owner, options);
			}
			options.add (this);
		}
	}

	/**  @deprecated */
	public CommandOption (Class owner, java.lang.String name, java.lang.String argName,
						  Class argType, boolean argRequired,
						  java.lang.String shortdoc)
	{
		this (owner, name, argName, argType, argRequired, shortdoc, null);
	}

	/** Give this CommandOption the opportunity to process the index'th argument in args.
		Return the next unprocessed index. */
	public int process (java.lang.String[] args, int index) {

		//System.out.println (name + " processing arg " + args[index]);
		if (args.length == 0)
			return index;

		//		System.out.println(index + ": " + args[index]);

		// Is there anything to process?
		if (index >= args.length ||
			args[index] == null || args[index].length() < 2 ||
			args[index].charAt(0) != '-' || args[index].charAt(1) != '-')
			return index;

		// Determine what the command name is
		java.lang.String optFullName = args[index].substring(2);
		int dotIndex = optFullName.lastIndexOf('.');
		java.lang.String optName = optFullName;

		// Commands may have a package prefix
		if (dotIndex != -1) {
			java.lang.String optPackageName = optFullName.substring (0, dotIndex);
			if (owner.getPackage() != null &&
				! owner.getPackage().toString().endsWith(optPackageName))
				return index;
			optName = optFullName.substring (dotIndex+1);
		}

		// Does the option name match the name of this CommandOption?
		if (! name.equals(optName))
			return index;

		// We have now determined that this CommandOption is the correct one
		this.invoked = true;
		index++;

		if (args.length > index && (args[index].length() < 2
									|| (args[index].charAt(0) != '-' && args[index].charAt(1) != '-'))) {
			index = parseArg (args, index);
		}
		else {
			if (argRequired) {
				throw new IllegalArgumentException ("Missing argument for option " + optName);
			}
			else {
				// xxx This is not parallel behavior to the above parseArg(String[],int) method.
				parseArg (args, -index); // xxx was ""
			}
			//index++;
		}
		return index;
	}

	public static BshInterpreter getInterpreter ()
	{
		return interpreter;
	}

	public static java.lang.String[] process (java.lang.Class owner, java.lang.String[] args)
	{
		CommandOption.List options = (CommandOption.List) class2options.get (owner);
		if (options == null)
			throw new IllegalArgumentException ("No CommandOptions registered for class "+owner);
		return options.process (args);
	}

	public static List getList (java.lang.Class owner)
	{
		CommandOption.List options = (CommandOption.List) class2options.get (owner);
		if (options == null)
			throw new IllegalArgumentException ("No CommandOptions registered for class "+owner);
		return options;
	}

	public static void setSummary (java.lang.Class owner, java.lang.String summary)
	{
		CommandOption.List options = (CommandOption.List) class2options.get (owner);
		if (options == null)
			throw new IllegalArgumentException ("No CommandOption.List registered for class "+owner);
		options.setSummary (summary);
	}

	public java.lang.String getFullName () {
		return fullName;
	}
  
	public java.lang.String getName () {
		return name;
	}

	public abstract java.lang.String defaultValueToString ();

	public abstract java.lang.String valueToString ();

	/** Return true is this CommandOption was matched by one of the processed arguments. */
	public boolean wasInvoked () {
		return invoked;
	}

	/** Called after this CommandOption matches an argument.

	    The default implementation simply calls parseArg(String), and
		returns index+1; unless index is negative, in which case it calls
		parseArg((String)null) and returns index. */
	public int parseArg (java.lang.String args[], int index) {
		if (index < 0) {
			parseArg ((java.lang.String)null);
			return index;
		}
		else {
			parseArg (args[index]);
			return index+1;
		}
	}

	public void parseArg (java.lang.String arg) {}

	/** To be overridden by subclasses;
	    "list" is the the CommandOption.List that called this option */
	public void postParsing (CommandOption.List list) {}

	/** For objects that can provide CommandOption.List's (which can be merged into other lists. */
	public static interface ListProviding {
		public CommandOption.List getCommandOptionList ();
	}

	public static void printOptionValues(Class owner) {
		CommandOption.List options = (CommandOption.List) class2options.get (owner);

		for (int i=0; i < options.size(); i++) {
			CommandOption option = options.getCommandOption(i);
			System.out.println(option.getName() + "\t=\t" + option.valueToString());
		}
	}

	public static class List {

		ArrayList options;
		HashMap map;
		java.lang.String summary;

		private List (java.lang.String summary) {
			this.options = new ArrayList ();
			this.map = new HashMap ();
			this.summary = summary;

			add (new Boolean (CommandOption.class, "help", "TRUE|FALSE", false, false,
							  "Print this command line option usage information.  "+
							  "Give argument of TRUE for longer documentation", null)
				{ public void postParsing(CommandOption.List list) { printUsage(value); System.exit(-1); } });
			add (new Object	(CommandOption.class, "prefix-code", "'JAVA CODE'", true, null,
							 "Java code you want run before any other interpreted code.  Note that the text "+
							 "is interpreted without modification, so unlike some other Java code options, "+
							 "you need to include any necessary 'new's when creating objects.", null));
			add (new File (CommandOption.class, "config", "FILE", false, null,
						   "Read command option values from a file", null)
				{ public void postParsing(CommandOption.List list) { readFromFile(value); } } );
		}

		public List (java.lang.String summary, CommandOption[] options) {
			this(summary);
			add(options);
		}

		public void setSummary (java.lang.String s)
		{
			this.summary = s;
		}

		public int size ()
		{
			return options.size();
		}

		public CommandOption getCommandOption (int index)
		{
			return (CommandOption) options.get(index);
		}

		public void add (CommandOption opt) {
			options.add (opt);
			map.put (opt.getFullName(), opt);
		}

		public void add (CommandOption[] opts) {
			for (int i = 0; i < opts.length; i++)
				add (opts[i]);
		}

		public void add (CommandOption.List opts) {
			for (int i = 0; i < opts.size(); i++)
				add (opts.getCommandOption(i));
		}

		public void add (Class owner) {
			CommandOption.List options = (CommandOption.List) class2options.get (owner);
			if (options == null)
				throw new IllegalArgumentException ("No CommandOptions registered for class "+owner);
			add (options);
		}

		/** 
		 *  Load configuration information from a file. If the filename ends 
		 *   with ".xml", the file is interpreted as a Java XML configuration file.
		 *   Otherwise it is interpreted as a text config file (eg "key = value" on each line).
		 *   Note that text files can only use Latin 1 (en-us) characters, while
		 *   XML files can be UTF-8.
		 */
		public void readFromFile(java.io.File configurationFile) {
			try {
				Properties properties = new Properties();
				
				if (configurationFile.getName().endsWith(".xml")) {
					properties.loadFromXML(new FileInputStream(configurationFile));
				}
				else {
					properties.load(new FileInputStream(configurationFile));
				}
				
				Enumeration keys = properties.propertyNames();
				while (keys.hasMoreElements()) {
					java.lang.String key = (java.lang.String) keys.nextElement();
					java.lang.String[] values = properties.getProperty(key).split("\\s+");
					
					boolean foundValue = false;
					for (int i = 0; i < options.size(); i++) {
						CommandOption option = (CommandOption) options.get(i);
						if (option.name.equals(key)) {
							foundValue = true;
							
							option.parseArg(values, 0);
							option.invoked = true;
							
							break;
						}
					}
				}
				
			} catch (Exception e) {
				System.err.println("Unable to process configuration file: " + e.getMessage());
			}
		}
		
		/** Parse and process command-line options in args.  Return sub-array of
			args occurring after first non-recognized arg that doesn't begin with a dash. */
		public java.lang.String[] process (java.lang.String[] args)
		{
			int index = 0;
			while (index < args.length) {
				int newIndex = index;
				for (int i = 0; i < options.size(); i++) {
					CommandOption o = (CommandOption)options.get(i);
					newIndex = o.process (args, index);
					if (newIndex != index) {
						o.postParsing(this);
						break;
					}
				}
				if (newIndex == index) {
					// All of the CommandOptions had their chance to claim the index'th option,,
					// but none of them did.
					printUsage(false);
					throw new IllegalArgumentException ("Unrecognized option " + index + ": " +args[index]);
				}
				index = newIndex;
			}
			return new java.lang.String[0];
		}

		public int processOptions (java.lang.String[] args)
		{
			for (int index = 0; index < args.length;) {
				int newIndex = index;
				for (int i = 0; i < options.size(); i++) {
					CommandOption o = (CommandOption)options.get(i);
					newIndex = o.process (args, index);
					if (newIndex != index) {
						o.postParsing(this);
						break;
					}
				}
				if (newIndex == index) {
					if (index < args.length && args[index].length() > 1 &&
						args[index].charAt(0) == '-' && args[index].charAt(1) == '-') {
						printUsage(false);
						throw new IllegalArgumentException ("Unrecognized option "+args[index]);
					}
					return index;
				}
				index = newIndex;
			}
			return args.length;
		}

		public void printUsage (boolean printLongDoc)
		{
			// xxx Fix this to have nicer formatting later.
			System.err.println (summary);
			for (int i = 0; i < options.size(); i++) {
				CommandOption o = (CommandOption) options.get(i);
				System.err.println ("--"+ o.name + " " + o.argName + "\n  " + o.shortdoc);
				if (o.longdoc != null && printLongDoc)
					System.err.println ("  "+o.longdoc);
				System.err.println ("  Default is "+o.defaultValueToString());
			}
		}

		public void logOptions (java.util.logging.Logger logger)
		{
			for (int i = 0; i < options.size(); i++) {
				CommandOption o = (CommandOption) options.get(i);
				logger.info (o.name+" = "+o.valueToString ());
			}
		}

	}


	public static class Boolean extends CommandOption
	{
		public boolean value, defaultValue;;
		public Boolean (Class owner, java.lang.String name, java.lang.String argName,
						boolean argRequired, boolean defaultValue,
						java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, Boolean.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public boolean value () { return value; }
		public void parseArg (java.lang.String arg) {
			if (arg == null || arg.equalsIgnoreCase("true") || arg.equals("1"))
				value = true;
			else if (arg.equalsIgnoreCase("false") || arg.equals("0"))
				value = false;
			else
				throw new IllegalArgumentException ("Boolean option should be true|false|0|1.  Instead found "+arg);
		}
		public java.lang.String defaultValueToString() { return java.lang.Boolean.toString(defaultValue); }
		public java.lang.String valueToString () { return java.lang.Boolean.toString (value); }
	}

	public static class Integer extends CommandOption
	{
		public int value, defaultValue;
		public Integer (Class owner, java.lang.String name, java.lang.String argName,
						boolean argRequired, int defaultValue,
						java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, Integer.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public int value () { return value; }
		public void parseArg (java.lang.String arg) { value = java.lang.Integer.parseInt(arg); }
		public java.lang.String defaultValueToString() { return java.lang.Integer.toString(defaultValue); }
		public java.lang.String valueToString () { return java.lang.Integer.toString (value); }
	}

	public static class IntegerArray extends CommandOption
	{
		public int[] value, defaultValue;
		public IntegerArray (Class owner, java.lang.String name, java.lang.String argName,
							 boolean argRequired, int[] defaultValue,
							 java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, IntegerArray.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public int[] value () { return value; }
		public void parseArg (java.lang.String arg) {
			java.lang.String elts[] = arg.split(",");
			value = new int[elts.length];
			for (int i = 0; i < elts.length; i++)
				value[i] = java.lang.Integer.parseInt(elts[i]);
		}
		public java.lang.String defaultValueToString() {
			StringBuffer b = new StringBuffer();
			java.lang.String sep = "";
			for (int i = 0; i < defaultValue.length; i++) {
				b.append(sep).append(java.lang.Integer.toString(defaultValue[i]));
				sep = ",";
			}
			return b.toString();
		}
		public java.lang.String valueToString() {
			StringBuffer b = new StringBuffer();
			java.lang.String sep = "";
			for (int i = 0; i < value.length; i++) {
				b.append(sep).append(java.lang.Integer.toString(value[i]));
				sep = ",";
			}
			return b.toString();
		}
	}

	public static class Double extends CommandOption
	{
		public double value, defaultValue;
		public Double (Class owner, java.lang.String name, java.lang.String argName,
					   boolean argRequired, double defaultValue,
					   java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, Double.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public double value () { return value; }
		public void parseArg (java.lang.String arg) { value = java.lang.Double.parseDouble(arg); }
		public java.lang.String defaultValueToString() { return java.lang.Double.toString(defaultValue); }
		public java.lang.String valueToString () { return java.lang.Double.toString (value); }
	}

	public static class DoubleArray extends CommandOption
	{
		public double[] value, defaultValue;
		public DoubleArray (Class owner, java.lang.String name, java.lang.String argName,
							boolean argRequired, double[] defaultValue,
							java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, IntegerArray.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public double[] value () { return value; }
		public void parseArg (java.lang.String arg) {
			java.lang.String elts[] = arg.split(",");
			value = new double[elts.length];
			for (int i = 0; i < elts.length; i++)
				value[i] = java.lang.Double.parseDouble(elts[i]);
		}
		public java.lang.String defaultValueToString() {
			StringBuffer b = new StringBuffer();
			java.lang.String sep = "";
			for (int i = 0; i < defaultValue.length; i++) {
				b.append(sep).append(java.lang.Double.toString(defaultValue[i]));
				sep = ",";
			}
			return b.toString();
		}
		public java.lang.String valueToString() {
			StringBuffer b = new StringBuffer();
			java.lang.String sep = "";
			for (int i = 0; i < value.length; i++) {
				b.append(sep).append(java.lang.Double.toString(value[i]));
				sep = ",";
			}
			return b.toString();
		}
	}

	public static class String extends CommandOption
	{
		public java.lang.String value, defaultValue;
		public String (Class owner, java.lang.String name, java.lang.String argName,
					   boolean argRequired, java.lang.String defaultValue,
					   java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, java.lang.String.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public java.lang.String value () { return value; }
		public void parseArg (java.lang.String arg) { value = arg; }
		public java.lang.String defaultValueToString() { return defaultValue; }
		public java.lang.String valueToString() { return value; }
	}

	public static class SpacedStrings extends CommandOption
	{
		public java.lang.String[] value, defaultValue;
		public SpacedStrings (Class owner, java.lang.String name, java.lang.String argName,
							  boolean argRequired, java.lang.String[] defaultValue,
							  java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, java.lang.String.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public java.lang.String[] value () { return value; }
		public int parseArg (java.lang.String args[], int index)
		{
			int count = 0;
			this.value = null;
			while (index < args.length
				   && (args[index].length() < 2
					   || (args[index].charAt(0) != '-' && args[index].charAt(1) != '-'))) {
				count++;
				java.lang.String[] oldValue = value;
				value = new java.lang.String[count];
				if (oldValue != null)
					System.arraycopy (oldValue, 0, value, 0, oldValue.length);
				value[count-1] = args[index];
				index++;
			}
			return index;
		}
		public java.lang.String defaultValueToString() {
			if (defaultValue == null)
				return "(null)";
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < defaultValue.length; i++) {
				sb.append (defaultValue[i]);
				if (i < defaultValue.length-1)
					sb.append (" ");
			}
			return sb.toString();
		}
		public java.lang.String valueToString () {
			if (value == null)
				return "(null)";

			java.lang.String val = "";
			for (int i = 0; i < value.length; i++) {
				val += value [i] + " ";
			}
			return val;
		}
	}

	public static class File extends CommandOption
	{
		public java.io.File value, defaultValue;
		public File (Class owner, java.lang.String name, java.lang.String argName,
					 boolean argRequired, java.io.File defaultValue,
					 java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, java.io.File.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public java.io.File value () { return value; }
		public void parseArg (java.lang.String arg) { value = new java.io.File(arg); }
		public java.lang.String defaultValueToString() { return defaultValue == null ? null : defaultValue.toString(); }
		public java.lang.String valueToString () { return value == null ? null : value.toString(); };
	}

	// Value is a string that can take on only a limited set of values
	public static class Set extends CommandOption
	{
		public java.lang.String value, defaultValue;
		java.lang.String[] setContents;
		java.lang.String contentsString;
		public Set (Class owner, java.lang.String name, java.lang.String argName,
					boolean argRequired, java.lang.String[] setContents, int defaultIndex,
					java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, java.io.File.class, argRequired, shortdoc, longdoc);
			this.defaultValue = this.value = setContents[defaultIndex];
			this.setContents = setContents;
			StringBuffer sb = new StringBuffer ();
			for (int i = 0; i < setContents.length; i++) {
				sb.append (setContents[i]);
				sb.append (",");
			}
			this.contentsString = sb.toString();
		}
		public java.lang.String value () { return value; }
		public void parseArg (java.lang.String arg)
		{
			value = null;
			for (int i = 0; i < setContents.length; i++)
				if (setContents[i].equals(arg))
					value = setContents[i];
			if (value == null)
				throw new IllegalArgumentException ("Unrecognized option argument \""+arg+"\" not in set "+contentsString);
		}
		public java.lang.String defaultValueToString() { return defaultValue; }
		public java.lang.String valueToString() { return value; }
	}


	public static class Object extends CommandOption
	{
		public java.lang.Object value, defaultValue;
		public Object (Class owner, java.lang.String name, java.lang.String argName,
					   boolean argRequired, java.lang.Object defaultValue,
					   java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, java.lang.Object.class, argRequired, shortdoc, longdoc);
			this.defaultValue = value = defaultValue;
		}
		public java.lang.Object value () { return value; }
		public void parseArg (java.lang.String arg) {
			try {
				value = interpreter.eval (arg);
			} catch (bsh.EvalError e) {
				throw new IllegalArgumentException ("Java interpreter eval error\n"+e);
			}
		}
		public java.lang.String defaultValueToString() { return defaultValue == null ? null : defaultValue.toString(); }
		public java.lang.String valueToString() { return value == null ? null : value.toString(); }
	}

	public static class ObjectFromBean extends Object
	{
		public ObjectFromBean (Class owner, java.lang.String name, java.lang.String argName,
							   boolean argRequired, java.lang.Object defValue,
							   java.lang.String shortdoc, java.lang.String longdoc)
		{
			super (owner, name, argName, argRequired, java.lang.Object.class, shortdoc, longdoc);
			defaultValue = value = defValue;
		}
		public java.lang.Object value () { return value; }
		public void parseArg (java.lang.String arg) {
			// parse something like MaxEntTrainer,gaussianPriorVariance=10,numIterations=20
			//System.out.println("Arg = " + arg);

			// First, split the argument at commas.
			java.lang.String fields[] = arg.split(",");

			//Massage constructor name, so that MaxEnt, MaxEntTrainer, new MaxEntTrainer()
			// all call new MaxEntTrainer()
			java.lang.String constructorName = fields[0];
			if (constructorName.contains("(") || constructorName.contains(";"))     // if contains ( or a ;, pass it though
				super.parseArg(arg);
			else {
				super.parseArg("new " + constructorName + "()"); // use default constructor to make the object
			}

			// Call the appropriate set... methods that appeared comma-separated
			// find methods associated with the class we just built
			Method methods[] =  this.value.getClass().getMethods();

			// find setters corresponding to parameter names.
			for (int i=1; i<fields.length; i++){
				java.lang.String nameValuePair[] = fields[i].split("=");
				java.lang.String parameterName  = nameValuePair[0];
				java.lang.String parameterValue = nameValuePair[1];  //todo: check for val present!
				java.lang.Object parameterValueObject;
				try {
					parameterValueObject = getInterpreter().eval(parameterValue);
				} catch (bsh.EvalError e) {
					throw new IllegalArgumentException ("Java interpreter eval error on parameter "+
					                                    parameterName + "\n"+e);
				}

				boolean foundSetter = false;
				for (int j=0; j<methods.length; j++){
					//					System.out.println("method " + j + " name is " + methods[j].getName());
					//					System.out.println("set" + Character.toUpperCase(parameterName.charAt(0)) + parameterName.substring(1));
					if ( ("set" + Character.toUpperCase(parameterName.charAt(0)) + parameterName.substring(1)).equals(methods[j].getName()) &&
						 methods[j].getParameterTypes().length == 1){
						//						System.out.println("Matched method " + methods[j].getName());
						//						Class[] ptypes = methods[j].getParameterTypes();
						//						System.out.println("Parameter types:");
						//						for (int k=0; k<ptypes.length; k++){
						//							System.out.println("class " + k + " = " + ptypes[k].getName());
						//						}

						try {
							java.lang.Object[] parameterList = new java.lang.Object[]{parameterValueObject};
							//							System.out.println("Argument types:");
							//							for (int k=0; k<parameterList.length; k++){
							//								System.out.println("class " + k + " = " + parameterList[k].getClass().getName());
							//							}
							methods[j].invoke(this.value, parameterList);
						} catch ( IllegalAccessException e) {
							System.out.println("IllegalAccessException " + e);
							throw new IllegalArgumentException ("Java access error calling setter\n"+e);
						}  catch ( InvocationTargetException e) {
							System.out.println("IllegalTargetException " + e);
							throw new IllegalArgumentException ("Java target error calling setter\n"+e);
						}
						foundSetter = true;
						break;
					}
				}
				if (!foundSetter){
	                System.out.println("Parameter " + parameterName + " not found on trainer " + constructorName);
					System.out.println("Available parameters for " + constructorName);
					for (int j=0; j<methods.length; j++){
						if ( methods[j].getName().startsWith("set") && methods[j].getParameterTypes().length == 1){
							System.out.println(Character.toLowerCase(methods[j].getName().charAt(3)) +
							                   methods[j].getName().substring(4));
						}
					}

					throw new IllegalArgumentException ("no setter found for parameter " + parameterName);
				}
			}

		}

		
		public java.lang.String defaultValueToString() { return defaultValue == null ? null : defaultValue.toString(); }
		public java.lang.String valueToString() { return value == null ? null : value.toString(); }
	}

}
