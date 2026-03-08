package com.thriftbazaar.backend.dto;

/**
 * Body for POST /messages
 */
public class SendMessageRequestDto {

    private Long   productId;
    private Long   receiverId;
    private String content;

    public Long   getProductId()  { return productId; }
    public Long   getReceiverId() { return receiverId; }
    public String getContent()    { return content; }

    public void setProductId(Long productId)    { this.productId = productId; }
    public void setReceiverId(Long receiverId)  { this.receiverId = receiverId; }
    public void setContent(String content)      { this.content = content; }
}
