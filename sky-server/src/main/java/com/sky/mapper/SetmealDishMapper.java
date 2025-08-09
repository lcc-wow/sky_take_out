package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.SetmealDish;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品id查询套餐id
     * @param ids
     * @return
     */
    List<Long> searchByDishId(List<Long> ids);

    /**
     * 批量插入套餐菜品数据
     * @param setmealDishes
     */
    @AutoFill(value = OperationType.INSERT)
    void insertBatch(List<SetmealDish> setmealDishes);

    /**
     * 根据套餐id查询套餐菜品数据
     * @param id
     * @return
     */
    List<SetmealDish> searchById(Long id);

    /**
     * 批量删除套餐菜品数据
     * @param ids
     */
    void deleteBatch(List<Long> ids);

}
