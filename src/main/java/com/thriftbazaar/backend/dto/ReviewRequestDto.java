package com.thriftbazaar.backend.dto;

/**
 * Payload sent by the client when submitting a product review.
 *
 * Fields
 * ──────
 * rating   – integer 1-5 (validated in ReviewService)
 * comment  – optional free-text (may be null or blank)
 */
public class ReviewRequestDto {

    private int    rating;
    private String comment;

    public ReviewRequestDto() {}

    public int    getRating()             { return rating; }
    public void   setRating(int rating)   { this.rating = rating; }

    public String getComment()            { return comment; }
    public void   setComment(String c)    { this.comment = c; }
}
