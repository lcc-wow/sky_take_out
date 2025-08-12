package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {

        //判断异常
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());

        ShoppingCart shoppingCart = new ShoppingCart();
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.search(shoppingCart);
        if (list == null || list.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //正常下单
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        //将基本订单数据存入数据库
        orderMapper.insert(orders);

        List<OrderDetail> ordersList = new ArrayList<>();
        //向订单明细表插入n条数据
        for (ShoppingCart cart : list) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            //设置订单id(存入基本信息后返回获取)
            orderDetail.setOrderId(orders.getId());
            ordersList.add(orderDetail);
        }

        //批量插入
        orderDetailMapper.insertBatch(ordersList);

        //删除购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        Map map = new HashMap();
        map.put("type",1);
        map.put("orderId",orders.getId());
        map.put("content","订单号"+outTradeNo);

        String json =JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);

    }

    /**
     * 历史订单查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());

        Page<Orders> orders = orderMapper.queryOrders(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (orders != null && !orders.isEmpty()) {
            // 根据订单ID列表查询订单详情
            for (Orders order : orders) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                List<OrderDetail> orderDetailList = orderDetailMapper.queryById(order.getId());
                orderVO.setOrderDetailList(orderDetailList);
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(orders.getTotal(), orderVOList);
    }

    /**
     * 订单详情
     *
     * @param id
     * @return
     */
    public OrderVO show(Long id) {
        List<OrderDetail> orderDetail = orderDetailMapper.queryById(id);
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetail);
        return orderVO;
    }

    /**
     * 用户端取消订单
     *
     * @param id
     */
    @Transactional
    public void cancel(Long id) {
        Orders order = orderMapper.getById(id);

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Integer status = order.getStatus();
        if (status == Orders.PENDING_PAYMENT || status == Orders.TO_BE_CONFIRMED) {
            Orders orders = Orders.builder()
                    .id(id)
                    .status(Orders.CANCELLED)
                    .cancelReason("用户取消")
                    .cancelTime(LocalDateTime.now())
                    .build();

            if (status == Orders.TO_BE_CONFIRMED) {
                orders.setPayStatus(Orders.REFUND);
                try {
                    //调用微信支付退款接口
                    weChatPayUtil.refund(
                            order.getNumber(), //商户订单号
                            order.getNumber(), //商户退款单号
                            new BigDecimal(0.01),//退款金额，单位 元
                            new BigDecimal(0.01));//原订单金额
                } catch (Exception e) {
                    throw new OrderBusinessException("取消订单失败");
                }
            }
            orderMapper.update(orders);

        } else if (status == Orders.CONFIRMED || status == Orders.DELIVERY_IN_PROGRESS) {
            throw new OrderBusinessException("订单暂时无法取消，如有需要请电话联系商家");
        }


    }

    /**
     * 再来一单(订单id)
     *
     * @param id
     */
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();

        ShoppingCart sc = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> scList = shoppingCartMapper.search(sc);

        if (scList != null && !scList.isEmpty()) {
            shoppingCartMapper.deleteByUserId(userId);
        }

        List<OrderDetail> orderDetailList = orderDetailMapper.queryById(id);
        List<ShoppingCart> list = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(BaseContext.getCurrentId());
            list.add(shoppingCart);
        }
        for (ShoppingCart cart : list) {
            shoppingCartMapper.insert(cart);
        }
    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Transactional
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders orders : page) {
            AddressBook addressBook = addressBookMapper.getById(orders.getAddressBookId());
            orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() +
                    addressBook.getDistrictName() + addressBook.getDetail());
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            orderVOList.add(orderVO);
        }

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 统计订单数据
     *
     * @return
     */
    public OrderStatisticsVO statistics() {
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Transactional
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Long orderId = ordersConfirmDTO.getId();
        Orders orders = Orders.builder()
                .id(orderId)
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Transactional
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {

        Long orderId = ordersRejectionDTO.getId();

        Orders order = orderMapper.getById(orderId);

        if (order == null || !order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setStatus(Orders.CANCELLED);
        order.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        order.setCancelTime(LocalDateTime.now());


        if (order.getPayStatus() == Orders.PAID) {
            try {
                //调用微信支付退款接口
                weChatPayUtil.refund(
                        order.getNumber(), //商户订单号
                        order.getNumber(), //商户退款单号
                        new BigDecimal(0.01),//退款金额，单位 元
                        new BigDecimal(0.01));//原订单金额
            } catch (Exception e) {
                throw new OrderBusinessException("取消订单失败");
            }
            order.setPayStatus(Orders.REFUND);
        }

        orderMapper.update(order);
    }

    /**
     * 管理端取消订单
     *
     * @param ordersCancelDTO
     */
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        Long orderId = ordersCancelDTO.getId();

        Orders order = orderMapper.getById(orderId);
        Integer payStatus = order.getPayStatus();

        if (payStatus == Orders.PAID) {
            try {
                //调用微信支付退款接口
                weChatPayUtil.refund(
                        order.getNumber(), //商户订单号
                        order.getNumber(), //商户退款单号
                        new BigDecimal(0.01),//退款金额，单位 元
                        new BigDecimal(0.01));//原订单金额
            } catch (Exception e) {
                throw new OrderBusinessException("取消订单失败");
            }
            order.setPayStatus(Orders.REFUND);
        }

        order.setStatus(Orders.CANCELLED);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(ordersCancelDTO.getCancelReason());

        orderMapper.update(order);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id) {
        Orders order = orderMapper.getById(id);

        //判断订单状态
        if (order == null || !order.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setId(id);
        order.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(order);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id){
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setStatus(Orders.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(order);
    }


    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    public void reminder(Long id){
        Orders orders = orderMapper.getById(id);

        if(orders == null ||  orders.getStatus() != Orders.TO_BE_CONFIRMED){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Map map = new HashMap();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content","订单号:"+orders.getNumber());
        String json = JSON.toJSONString(map);

        webSocketServer.sendToAllClient(json);
    }
}
