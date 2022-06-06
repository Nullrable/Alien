package org.lsd.alien.demo.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * (Location)表服务实现类.
 *
 * @author nhsoft.hecs
 * @author nhsoft.lsd
 */
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Slf4j
public class Location implements Serializable {
    
    private static final long serialVersionUID = 300678949312134113L;
    
    /**
     * 门店 id.
     */
    private BigInteger id;

    /**
     * 代码.
     */
    private String code;

    /**
     * 门店名称.
     */
    private String name;

    /**
     * 简介.
     */
    private String description;

    /**
     * 图片ID.
     */
    private BigInteger imageId;

    /**
     * 维度.
     */
    private BigDecimal latitude;

    /**
     * 经度.
     */
    private BigDecimal longitude;

    /**
     * 是否这个门店是活跃的.
     */
    private Boolean active;
    
    /**
     * 第一行门店地址.
     */
    private String address;

    /**
     * 门牌号.
     */
    private String houseNo;

    /**
     * 城市.
     */
    private String city;
    
    /**
     * 国家.
     */
    private String country;
   
    /**
     * 国家code，ISO_3166.
     */
    private String countryCode;
    
    /**
     * 创建时间.
     */
    private Date createdAt;
    
    /**
     * 更新时间.
     */
    private Date updatedAt;
    
    /**
     * boolean类型，是否是fulfillment service（运送服务）的门店，true指是一个运送服务门店.
     */
    private Boolean legacy;

    /**
     * 门店手机号，这个值支持包含+或者-这种字符.
     */
    private String phone;
    
    /**
     * 省.
     */
    private String province;
    
    /**
     * 省的code，国外应该是两位字符串，国内是否也可两位.
     */
    private String provinceCode;
    
    /**
     * 邮编.
     */
    private String zip;
    
    /**
     * 本地化的国家名称，比如：中国，china.
     */
    private String localizedCountryName;
    
    /**
     * 本地化的省名称，比如：浙江、ZheJiang.
     */
    private String localizedProvinceName;
    
    /**
     * 商户id.
     */
    private BigInteger merchantId;
    
    /**
     * 逻辑删除.
     */
    private Boolean deleted;
    
    /**
     * 删除时间.
     */
    private Date deletedAt;

    /**
     * 排序号.
     */
    private Integer position;

    /**
     * 与其他门店隔离.
     */
    private Boolean isolation;

    /**
     * 联系人姓名.
     */
    private String linkerName;

    /**
     * 速记码，例如 苹果： 苹果， 值为PG.
     */
    private String shorthandCharacter;

    /**
     * 速记码首字母，例如 name： 苹果， 值为P.
     */
    private String firstCharacter;

    /**
     * 城市代码.
     */
    private String cityCode;

    /**
     * 区.
     */
    private String district;

    /**
     * 区代码.
     */
    private String districtCode;

    /**
     * 启用本地配送.
     */
    private Boolean enableLocalDeliver;

    /**
     * 启用自提.
     */
    private Boolean enablePickUp;

    /**
     * 启用快递配送.
     */
    private Boolean enableDeliver;

    /**
     * 门店区域ID.
     */
    private BigInteger locationZoneId;

    /**
     * 是否总部门店.
     */
    private Boolean headquarters;

    /**
     * 省市区.
     */
    private String shortAddress;

    /**
     * 门店营业时间.
     */
    private String businessTimeRange;

    /**
     * 有效期至.
     */
    private Date expiredAt;

    /**
     * 创建人.
     */
    private String creator;

    /**
     * pos门店点位个数.
     */
    private Integer posLocationLimit;

    //非持久化字段
    /**
     * 修改者.
     */
    private String updator;
   
    /**
     * 接替门店id.
     */
    private BigInteger relieverLocationId;

    /**
     * 门店到指定经纬度距离.
     */
    private BigDecimal distance;

    /**
     * 支付链接.
     */
    private String payUrl;
}
