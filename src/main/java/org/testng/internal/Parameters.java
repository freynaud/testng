package org.testng.internal;

import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.TestNGException;
import org.testng.annotations.IConfigurationAnnotation;
import org.testng.annotations.IDataProviderAnnotation;
import org.testng.annotations.IFactoryAnnotation;
import org.testng.annotations.IParameterizable;
import org.testng.annotations.IParametersAnnotation;
import org.testng.annotations.ITestAnnotation;
import org.testng.collections.Lists;
import org.testng.internal.ParameterHolder.ParameterOrigin;
import org.testng.internal.annotations.AnnotationHelper;
import org.testng.internal.annotations.IAnnotationFinder;
import org.testng.internal.annotations.IDataProvidable;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Methods that bind parameters declared in testng.xml to actual values
 * used to invoke methods.
 *
 * @author <a href="mailto:cedric@beust.com">Cedric Beust</a>
 * @author <a href='mailto:the_mindstorm[at]evolva[dot]ro'>Alexandru Popescu</a>
 *
 * @@ANNOTATIONS@@
 */
public class Parameters {
  public static final String NULL_VALUE= "null";

  /**
   * Creates the parameters needed for constructing a test class instance.
   * @param finder TODO
   */
  public static Object[] createInstantiationParameters(Constructor ctor,
      String methodAnnotation,
      IAnnotationFinder finder,
      String[] parameterNames,
      Map<String, String> params, XmlSuite xmlSuite)
  {
    return createParameters(ctor.toString(), ctor.getParameterTypes(),
        finder.findOptionalValues(ctor), methodAnnotation, finder, parameterNames, new MethodParameters(params), xmlSuite);
  }

  /**
   * Creates the parameters needed for the specified <tt>@Configuration</tt> <code>Method</code>.
   *
   * @param m the configuraton method
   * @param params
   * @param currentTestMethod the current @Test method or <code>null</code> if no @Test is available (this is not
   *    only in case the configuration method is a @Before/@AfterMethod
   * @param finder the annotation finder
   * @param xmlSuite
   * @return
   */
  public static Object[] createConfigurationParameters(Method m,
      Map<String, String> params,
      Object[] parameterValues,
      ITestNGMethod currentTestMethod,
      IAnnotationFinder finder,
      XmlSuite xmlSuite,
      ITestContext ctx,
      ITestResult testResult)
  {
    Method currentTestMeth= currentTestMethod != null ?
        currentTestMethod.getMethod() : null;
    return createParameters(m,
        new MethodParameters(params, parameterValues, currentTestMeth, ctx, testResult),
        finder, xmlSuite, IConfigurationAnnotation.class, "@Configuration");
  }

  ////////////////////////////////////////////////////////

  public static Object getInjectedParameter(Class<?> c, Method method, ITestContext context,
      ITestResult testResult) {
    Object result = null;
    if (Method.class.equals(c)) {
      result = method;
    }
    else if (ITestContext.class.equals(c)) {
      result = context;
    }
    else if (XmlTest.class.equals(c)) {
      result = context.getCurrentXmlTest();
    }
    else if (ITestResult.class.equals(c)) {
      result = testResult;
    }
    return result;
  }

  /**
   * @param optionalValues TODO
   * @param finder TODO
   * @param parameterAnnotations TODO
   * @param m
   * @param instance
   * @return An array of parameters suitable to invoke this method, possibly
   * picked from the property file
   */
  private static Object[] createParameters(String methodName,
      Class[] parameterTypes,
      String[] optionalValues,
      String methodAnnotation,
      IAnnotationFinder finder,
      String[] parameterNames, MethodParameters params, XmlSuite xmlSuite)
  {
    Object[] result = new Object[0];
    if(parameterTypes.length > 0) {
      List<Object> vResult = Lists.newArrayList();

      checkParameterTypes(methodName, parameterTypes, methodAnnotation, parameterNames);

      for(int i = 0, j = 0; i < parameterTypes.length; i++) {
        Object inject = getInjectedParameter(parameterTypes[i], params.currentTestMethod,
            params.context, params.testResult);
        if (inject != null) {
          vResult.add(inject);
        }
        else {
          if (j < parameterNames.length) {
            String p = parameterNames[j];
            String value = params.xmlParameters.get(p);
            if(null == value) {
              // try SysEnv entries
              value= System.getProperty(p);
            }
            if (null == value) {
              if (optionalValues != null) {
                value = optionalValues[i];
              }
              if (null == value) {
              throw new TestNGException("Parameter '" + p + "' is required by "
                  + methodAnnotation
                  + " on method "
                  + methodName
                  + "\nbut has not been marked @Optional or defined "
                  + (xmlSuite.getFileName() != null ? "in "
                  + xmlSuite.getFileName() : ""));
              }
            }

            vResult.add(convertType(parameterTypes[i], value, p));
            j++;
          }
        }
      }

      result = vResult.toArray(new Object[vResult.size()]);
    }

    return result;
  }

  private static void checkParameterTypes(String methodName,
      Class[] parameterTypes, String methodAnnotation, String[] parameterNames)
  {
    int totalLength = parameterTypes.length;
    Set<Class> injectedTypes = new HashSet<Class>() {
      private static final long serialVersionUID = -5324894581793435812L;

    {
      add(ITestContext.class);
      add(ITestResult.class);
      add(XmlTest.class);
      add(Method.class);
      add(Object[].class);
    }};
    for (Class parameterType : parameterTypes) {
      if (injectedTypes.contains(parameterType)) {
        totalLength--;
      }
    }

    if (parameterNames.length != totalLength) {
      throw new TestNGException( "Method " + methodName + " requires "
          + parameterTypes.length + " parameters but "
          + parameterNames.length
          + " were supplied in the "
          + methodAnnotation
          + " annotation.");
    }
  }

  public static Object convertType(Class type, String value, String paramName) {
    Object result = null;

    if(NULL_VALUE.equals(value.toLowerCase())) {
      if(type.isPrimitive()) {
        Utils.log("Parameters", 2, "Attempt to pass null value to primitive type parameter '" + paramName + "'");
      }

      return null; // null value must be used
    }

    if(type == String.class) {
      result = value;
    }
    else if(type == int.class || type == Integer.class) {
      result = Integer.valueOf(Integer.parseInt(value));
    }
    else if(type == boolean.class || type == Boolean.class) {
      result = Boolean.valueOf(value);
    }
    else if(type == byte.class || type == Byte.class) {
      result = Byte.valueOf(Byte.parseByte(value));
    }
    else if(type == char.class || type == Character.class) {
      result = Character.valueOf(value.charAt(0));
    }
    else if(type == double.class || type == Double.class) {
      result = Double.valueOf(Double.parseDouble(value));
    }
    else if(type == float.class || type == Float.class) {
      result = Float.valueOf(Float.parseFloat(value));
    }
    else if(type == long.class || type == Long.class) {
      result = Long.valueOf(Long.parseLong(value));
    }
    else if(type == short.class || type == Short.class) {
      result = Short.valueOf(Short.parseShort(value));
    }
    else {
      assert false : "Unsupported type parameter : " + type;
    }

    return result;
  }

  private static DataProviderHolder findDataProvider(Class clazz, ConstructorOrMethod m,
      IAnnotationFinder finder) {
    DataProviderHolder result = null;

    IDataProvidable dp = findDataProviderInfo(clazz, m, finder);
    if (dp != null) {
      String dataProviderName = dp.getDataProvider();
      Class dataProviderClass = dp.getDataProviderClass();

      if (! Utils.isStringEmpty(dataProviderName)) {
        result = findDataProvider(clazz, finder, dataProviderName, dataProviderClass);

        if(null == result) {
          throw new TestNGException("Method " + m + " requires a @DataProvider named : "
              + dataProviderName + (dataProviderClass != null ? " in class " + dataProviderClass.getName() : "")
              );
        }
      }
    }

    return result;
  }

  /**
   * Find the data provider info (data provider name and class) on either @Test(dataProvider),
   * @Factory(dataProvider) on a method or @Factory(dataProvider) on a constructor.
   */
  private static IDataProvidable findDataProviderInfo(Class clazz, ConstructorOrMethod m,
      IAnnotationFinder finder) {
    IDataProvidable result = null;

    if (m.getMethod() != null) {
      //
      // @Test(dataProvider)
      //
      result = AnnotationHelper.findTest(finder, m.getMethod());
      if (result == null) {
        result = AnnotationHelper.findTest(finder, clazz);
      }
      if (result == null) {
        //
        // @Factory(dataProvider) on a method
        //
        result = AnnotationHelper.findFactory(finder, m.getMethod());
      }
    } else {
      //
      // @Factory(dataProvider) on a constructor
      //
      result = AnnotationHelper.findFactory(finder, m.getConstructor());
    }

    return result;
  }

  /**
   * Find a method that has a @DataProvider(name=name)
   */
  private static DataProviderHolder findDataProvider(Class cls, IAnnotationFinder finder,
      String name, Class dataProviderClass)
  {
    DataProviderHolder result = null;

    boolean shouldBeStatic = false;
    if (dataProviderClass != null) {
      cls = dataProviderClass;
      shouldBeStatic = true;
    }

    for (Method m : ClassHelper.getAvailableMethods(cls)) {
      IDataProviderAnnotation dp = (IDataProviderAnnotation)
          finder.findAnnotation(m, IDataProviderAnnotation.class);
      if (null != dp && (name.equals(dp.getName()) || name.equals(m.getName()))) {
        if (shouldBeStatic && (m.getModifiers() & Modifier.STATIC) == 0) {
          throw new TestNGException("DataProvider should be static: " + m);
        }

        if (result != null) {
          throw new TestNGException("Found two providers called '" + name + "' on " + cls);
        }
        result = new DataProviderHolder(dp, m);
      }
    }

    return result;
  }

  @SuppressWarnings({"deprecation"})
  private static Object[] createParameters(Method m, MethodParameters params,
      IAnnotationFinder finder, XmlSuite xmlSuite, Class annotationClass, String atName)
  {
    List<Object> result = Lists.newArrayList();

    Object[] extraParameters = new Object[0];
    //
    // Try to find an @Parameters annotation
    //
    IParametersAnnotation annotation = (IParametersAnnotation) finder.findAnnotation(m, IParametersAnnotation.class);
    Class<?>[] types = m.getParameterTypes();
    if(null != annotation) {
      String[] parameterNames = annotation.getValue();
      extraParameters = createParameters(m.getName(), types,
          finder.findOptionalValues(m), atName, finder, parameterNames, params, xmlSuite);
    }

    //
    // Else, use the deprecated syntax
    //
    else {
      IParameterizable a = (IParameterizable) finder.findAnnotation(m, annotationClass);
      if(null != a && a.getParameters().length > 0) {
        String[] parameterNames = a.getParameters();
        extraParameters = createParameters(m.getName(), types,
            finder.findOptionalValues(m), atName, finder, parameterNames, params, xmlSuite);
      }
      else {
        extraParameters = createParameters(m.getName(), types,
            finder.findOptionalValues(m), atName, finder, new String[0], params, xmlSuite);
      }
    }

    //
    // Add the extra parameters we found
    //
    for (Object p : extraParameters) {
      result.add(p);
    }

    // If the method declared an Object[] parameter and we have parameter values, inject them
    for (int i = 0; i < types.length; i++) {
        if (Object[].class.equals(types[i])) {
            result.add(i, params.parameterValues);
        }
    }


    return result.toArray(new Object[result.size()]);
  }

  /**
   * If the method has parameters, fill them in. Either by using a @DataProvider
   * if any was provided, or by looking up <parameters> in testng.xml
   * @return An Iterator over the values for each parameter of this
   * method.
   */
  public static ParameterHolder handleParameters(ITestNGMethod testMethod,
      Map<String, String> allParameterNames,
      Object instance,
      MethodParameters methodParams,
      XmlSuite xmlSuite,
      IAnnotationFinder annotationFinder,
      Object fedInstance)
  {
    ParameterHolder result;
    Iterator<Object[]> parameters = null;

    /*
     * Do we have a @DataProvider? If yes, then we have several
     * sets of parameters for this method
     */
    DataProviderHolder dataProviderHolder =
        findDataProvider(testMethod.getTestClass().getRealClass(),
            testMethod.getConstructorOrMethod(), annotationFinder);

    if (null != dataProviderHolder) {
      int parameterCount = testMethod.getConstructorOrMethod().getParameterTypes().length;

      for (int i = 0; i < parameterCount; i++) {
        String n = "param" + i;
        allParameterNames.put(n, n);
      }

      parameters = MethodInvocationHelper.invokeDataProvider(
          instance, /* a test instance or null if the dataprovider is static*/
          dataProviderHolder.method,
          testMethod,
          methodParams.context,
          fedInstance,
          annotationFinder);

      Iterator<Object[]> filteredParameters = filterParameters(parameters,
          testMethod.getInvocationNumbers());

      result = new ParameterHolder(filteredParameters, ParameterOrigin.ORIGIN_DATA_PROVIDER,
          dataProviderHolder);
    }
    else {
      //
      // Normal case: we have only one set of parameters coming from testng.xml
      //
      allParameterNames.putAll(methodParams.xmlParameters);
      // Create an Object[][] containing just one row of parameters
      Object[][] allParameterValuesArray = new Object[1][];
      allParameterValuesArray[0] = createParameters(testMethod.getMethod(),
          methodParams, annotationFinder, xmlSuite, ITestAnnotation.class, "@Test");

      // Mark that this method needs to have at least a certain
      // number of invocations (needed later to call AfterGroups
      // at the right time).
      testMethod.setParameterInvocationCount(allParameterValuesArray.length);
      // Turn it into an Iterable
      parameters = MethodHelper.createArrayIterator(allParameterValuesArray);

      result = new ParameterHolder(parameters, ParameterOrigin.ORIGIN_XML,
          dataProviderHolder);
    }

    return result;
  }

  /**
   * If numbers is empty, return parameters, otherwise, return a subset of parameters
   * whose ordinal number match these found in numbers.
   */
  static private Iterator<Object[]> filterParameters(Iterator<Object[]> parameters,
      List<Integer> list) {
    if (list.isEmpty()) {
      return parameters;
    } else {
      List<Object[]> result = Lists.newArrayList();
      int i = 0;
      while (parameters.hasNext()) {
        Object[] next = parameters.next();
        if (list.contains(i)) {
          result.add(next);
        }
        i++;
      }
      return new ArrayIterator(result.toArray(new Object[list.size()][]));
    }
  }

  private static void ppp(String s) {
    System.out.println("[Parameters] " + s);
  }

  /** A parameter passing helper class. */
  public static class MethodParameters {
    private final Map<String, String> xmlParameters;
    private final Method currentTestMethod;
    private final ITestContext context;
    private Object[] parameterValues;
    public ITestResult testResult;

    public MethodParameters(Map<String, String> params) {
      this(params, null, null, null, null);
    }

    public MethodParameters(Map<String, String> params, Method m) {
      this(params, null, m, null, null);
    }

    public MethodParameters(Map<String, String> params, Object[] pv, Method m, ITestContext ctx,
        ITestResult tr) {
      xmlParameters = params;
      currentTestMethod = m;
      context = ctx;
      parameterValues = pv;
      testResult = tr;
    }
  }
}
