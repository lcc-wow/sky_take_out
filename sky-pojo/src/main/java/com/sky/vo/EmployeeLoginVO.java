package com.sky.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "员工登录返回的数据格式")
public class EmployeeLoginVO implements Serializable {

    @ApiModelProperty("主键值")
    private Long id;

    @ApiModelProperty("用户名")
    private String userName;

    @ApiModelProperty("姓名")
    private String name;

    @ApiModelProperty("jwt令牌")
    private String token;


    private EmployeeLoginVO(Builder builder) {
        this.id = builder.id;
        this.userName = builder.userName;
        this.name = builder.name;
        this.token = builder.token;
    }

    // Getter 方法
    public Long getId() {
        return id;
    }

    public String getUserName() {
        return userName;
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    // 静态 Builder 类
    public static class Builder {
        private Long id;
        private String userName;
        private String name;
        private String token;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        // 构建 EmployeeLoginVO 实例
        public EmployeeLoginVO build() {
            return new EmployeeLoginVO(this);
        }
    }

    // 静态 builder 方法
    public static Builder builder() {
        return new Builder();
    }
}
