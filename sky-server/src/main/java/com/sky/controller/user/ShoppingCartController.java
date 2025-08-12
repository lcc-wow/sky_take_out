package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartServer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/shoppingCart")
@Api(tags = "C端-购物车接口")
@Slf4j
public class ShoppingCartController {

    @Autowired
    private ShoppingCartServer shoppingCartServer;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     * @return
     */
    @PostMapping("/add")
    @ApiOperation("添加购物车")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("添加购物车：{}", shoppingCartDTO);
        shoppingCartServer.add(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 获取当前用户的购物车数据
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("获取当前用户的购物车数据")
    public Result<List<ShoppingCart>> list() {
        List<ShoppingCart> list = shoppingCartServer.showList();
        return Result.success(list);
    }

    /**
     * 清空购物车
     * @return
     */
    @DeleteMapping("/clean")
    @ApiOperation("清空购物车")
    public Result clean() {
        shoppingCartServer.clean();
        return Result.success();
    }

    @PostMapping("/sub")
    @ApiOperation("减少购物车内物品")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("购物车数据:{}", shoppingCartDTO);
        shoppingCartServer.sub(shoppingCartDTO);
        return Result.success();
    }

}
