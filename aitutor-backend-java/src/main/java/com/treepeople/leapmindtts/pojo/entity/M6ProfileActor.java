package com.treepeople.leapmindtts.pojo.entity;

/** Minimal user projection used only to authorize M6 profile requests. */
public class M6ProfileActor {
    private Long id;
    private String username;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
