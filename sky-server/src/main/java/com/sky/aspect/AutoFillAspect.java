package com.sky.aspect;


import com.sky.annotation.AutoFill;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.time.LocalDateTime;


@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut(){}

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint){
        log.info("开始进行数据填充");

        //获取当前被拦截的方法注解中的信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();

        //获取当前被拦截的方法的参数
        Object[] args = joinPoint.getArgs();
        if(args == null || args.length == 0){
            return;
        }
        Object object = args[0];

        //准备要填充的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        //根据对应的操作类型，为对应的属性赋值
        if(operationType == OperationType.INSERT){
            try {
                Method setCreateTime = object.getClass().getDeclaredMethod("setCreateTime", LocalDateTime.class);
                Method setUpdateTime = object.getClass().getDeclaredMethod("setUpdateTime", LocalDateTime.class);
                Method setCreateUser = object.getClass().getDeclaredMethod("setCreateUser", Long.class);
                Method setUpdateUser = object.getClass().getDeclaredMethod("setUpdateUser", Long.class);

                setCreateTime.invoke(object, now);
                setUpdateTime.invoke(object, now);
                setCreateUser.invoke(object, currentId);
                setUpdateUser.invoke(object, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (operationType == OperationType.UPDATE) {
            try {
                Method setUpdateTime = object.getClass().getDeclaredMethod("setUpdateTime", LocalDateTime.class);
                Method setUpdateUser = object.getClass().getDeclaredMethod("setUpdateUser", Long.class);

                setUpdateTime.invoke(object, now);
                setUpdateUser.invoke(object, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
