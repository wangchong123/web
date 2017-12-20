package mvc.tools;



import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class AsmTools {

    public static String[] getMethodParameterNamesByAsm(final Class cls, final Method method){
        final String methodName = method.getName();
        final Class<?>[] methodParameterTypes = method.getParameterTypes();
        final int methodParameterCount = methodParameterTypes.length;
        String className = method.getDeclaringClass().getName();
        final boolean isStatic = Modifier.isStatic(method.getModifiers());
        final String[] methodParametersNames = new String[methodParameterCount];
        int lastDotIndex = className.lastIndexOf(".");
        className = className.substring(lastDotIndex + 1) + ".class";
        InputStream is = cls.getResourceAsStream(className);
        try {
            ClassReader cr = new ClassReader(is);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cr.accept(new ClassAdapter(cw) {
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

                    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                    final Type[] argTypes = Type.getArgumentTypes(desc);

                    //参数类型不一致
                    if (!methodName.equals(name) || !matchType(argTypes, methodParameterTypes)) {
                        return mv;
                    }
                    return new MethodAdapter(mv) {
                        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                            //如果是静态方法，第一个参数就是方法参数，非静态方法，则第一个参数是 this ,然后才是方法的参数
                            int methodParameterIndex = isStatic ? index : index - 1;
                            if (0 <= methodParameterIndex && methodParameterIndex < methodParameterCount) {
                                methodParametersNames[methodParameterIndex] = name;
                            }
                            super.visitLocalVariable(name, desc, signature, start, end, index);
                        }
                    };
                }
            }, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return methodParametersNames;

    }

    private static boolean matchType(Type[] types,Class<?>[] parameterTypes){
        if(types.length != parameterTypes.length){
            return false;
        }
        for(int i=0;i<types.length;i++){
            if(Type.getType(parameterTypes[i]).equals(types[i])){
                return true;
            }
        }
        return false;
    }
}
