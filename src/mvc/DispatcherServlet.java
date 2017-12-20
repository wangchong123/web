package mvc;

import mvc.annotation.*;
import mvc.tools.AsmTools;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DispatcherServlet extends HttpServlet {

    private List<String> classList = new ArrayList<>();

    private Map<String, Object> classMap = new HashMap<>();

    private Map<String, Object> handleMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            boolean isMatcher = pattern(req, resp);
            if (!isMatcher) {
                out(resp,"404 not found");
            }
        } catch (Exception ex) {
            ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            ex.printStackTrace(new java.io.PrintWriter(buf, true));
            String expMessage = buf.toString();
            buf.close();
            out(resp, "500 Exception" + "\n" + expMessage);
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("---------init--------");
        String pathName = config.getInitParameter("scanPackage");
        scanPackage(pathName);
        doInstance();
        doAutoWired();
        doHandleMapping();
    }

    private void out(HttpServletResponse response, String str) {
        try {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().print(str);
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    private void scanPackage(String packageName) {
        URL url = getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File file = new File(url.getFile());
        for (File f : file.listFiles()) {
            if (f.isDirectory()) {
                scanPackage(packageName + "." + f.getName());
            } else {
                if (f.getName().endsWith(".class")) {
                    String className = packageName + "." + f.getName().replace(".class", "");
                    try {
                        Class<?> cls = Class.forName(className);
                        if (cls.isAnnotationPresent(Controller.class) || cls.isAnnotationPresent(Service.class)) {
                            classList.add(className);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void doInstance() {
        if (classList.size() == 0) {
            return;
        }
        classList.forEach(c -> {
            try {
                Class<?> cls = Class.forName(c);
                if (cls.isAnnotationPresent(Controller.class)) {
                    classMap.put(lowerFirstChar(cls.getSimpleName()), cls.newInstance());
                } else if (cls.isAnnotationPresent(Service.class)) {
                    Service service = cls.getAnnotation(Service.class);
                    String value = service.value();
                    if (!"".equals(value.trim())) {
                        classMap.put(value, cls.newInstance());
                    } else {
                        Class[] inte = cls.getInterfaces();
                        classMap.put(lowerFirstChar(inte[0].getSimpleName()), cls.newInstance());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

    private void doAutoWired() {
        if (classMap.isEmpty()) {
            return;
        }
        classMap.entrySet().forEach(c -> {
            Field[] fields = c.getValue().getClass().getDeclaredFields();
            for (Field f : fields) {
                if (f.isAnnotationPresent(Autowird.class)) {
                    String beanName;
                    Autowird autowird = f.getAnnotation(Autowird.class);
                    String value = autowird.value();
                    if (!"".equals(value)) {
                        beanName = value;
                    } else {
                        beanName = lowerFirstChar(f.getType().getSimpleName());
                    }
                    f.setAccessible(true);
                    if (classMap.get(beanName) != null) {
                        try {
                            f.set(c.getValue(), classMap.get(beanName));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private void doHandleMapping() {
        if (classMap.isEmpty()) {
            return;
        }
        classMap.entrySet().forEach(c -> {
            Class<?> cls = c.getValue().getClass();
            if (cls.isAnnotationPresent(Controller.class)) {
                String url = "/";
                if (cls.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping classMapping = cls.getAnnotation(RequestMapping.class);
                    url += classMapping.value();
                }

                Method[] methods = cls.getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping methodMapping = method.getAnnotation(RequestMapping.class);
                        String realUrl = url + "/" + methodMapping.value();
                        realUrl = realUrl.replaceAll("/+", "/");
                        Annotation[][] annotations = method.getParameterAnnotations();
                        Map<String, Integer> paramMap = new HashMap<>();
                        String[] paramsName = AsmTools.getMethodParameterNamesByAsm(cls, method);
                        Class<?>[] paramTypes = method.getParameterTypes();
                        for (int i = 0; i < annotations.length; i++) {
                            Annotation[] anno = annotations[i];
                            if (anno.length == 0) {
                                Class<?> type = paramTypes[i];
                                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                                    paramMap.put(type.getName(), i);
                                } else {
                                    paramMap.put(paramsName[i], i);
                                }
                                continue;
                            }
                            for (Annotation an : anno) {
                                if (an.annotationType() == RequestParam.class) {
                                    String valueName = ((RequestParam) an).value();
                                    if (!"".equals(valueName.trim())) {
                                        paramMap.put(valueName, i);
                                    }
                                }
                            }
                        }
                        HandleModel handleModel = new HandleModel(method, c.getValue(), paramMap);
                        handleMapping.put(realUrl, handleModel);
                    }
                }
            }
        });
    }


    private class HandleModel {
        Method method;
        Object controller;
        Map<String, Integer> paramMap;

        public HandleModel(Method method, Object controller, Map<String, Integer> paramMap) {
            this.method = method;
            this.controller = controller;
            this.paramMap = paramMap;
        }
    }


    private boolean pattern(HttpServletRequest request, HttpServletResponse response) throws InvocationTargetException, IllegalAccessException {
        if (handleMapping.isEmpty()) {
            return false;
        }
        //用户请求地址
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        //用户写了多个"///"，只保留一个
        requestUri = requestUri.replace(contextPath, "").replaceAll("/+", "/");

        //遍历HandlerMapping，寻找url匹配的
        for (Map.Entry<String, Object> entry : handleMapping.entrySet()) {
            if (entry.getKey().equals(requestUri)) {
                //取出对应的HandlerModel
                HandleModel handlerModel = (HandleModel) entry.getValue();

                Map<String, Integer> paramIndexMap = handlerModel.paramMap;
                //定义一个数组来保存应该给method的所有参数赋值的数组
                Object[] paramValues = new Object[paramIndexMap.size()];

                Class<?>[] types = handlerModel.method.getParameterTypes();

                //遍历一个方法的所有参数[name->0,addr->1,HttpServletRequest->2]
                for (Map.Entry<String, Integer> param : paramIndexMap.entrySet()) {
                    String key = param.getKey();
                    if (key.equals(HttpServletRequest.class.getName())) {
                        paramValues[param.getValue()] = request;
                    } else if (key.equals(HttpServletResponse.class.getName())) {
                        paramValues[param.getValue()] = response;
                    } else {
                        //如果用户传了参数，譬如 name= "wolf"，做一下参数类型转换，将用户传来的值转为方法中参数的类型
                        String parameter = request.getParameter(key);
                        if (parameter != null) {
                            paramValues[param.getValue()] = convert(parameter.trim(), types[param.getValue()]);
                        }
                    }
                }
                //激活该方法
                handlerModel.method.invoke(handlerModel.controller, paramValues);
                return true;
            }
        }

        return false;
    }


    private Object convert(String param, Class<?> targetType) {
        if (targetType == String.class) {
            return param;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(param);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(param);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (param.toLowerCase().equals("true") || param.equals("1")) {
                return true;
            } else if (param.toLowerCase().equals("false") || param.equals("0")) {
                return false;
            }
            throw new RuntimeException("不支持的参数");
        } else {
            return null;
        }
    }


    private String lowerFirstChar(String str) {
        char[] ch = str.toCharArray();
        ch[0] += 32;
        return String.valueOf(ch);
    }
}
