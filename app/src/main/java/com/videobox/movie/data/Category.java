package com.videobox.movie.data;

/** 播放源动态分类（type_id / type_pid / type_name） */
public class Category {
    public int id;
    public int pid = -1;   // 父分类 id；-1 表示缺失（当作主分类）
    public String name;

    public Category() { }

    public Category(int id, int pid, String name) {
        this.id = id;
        this.pid = pid;
        this.name = name;
    }

    public boolean isMain() { return pid <= 0; }
}
