package com.sixlengs.path2mysql;

import cn.hutool.core.util.StrUtil;
import com.sixlengs.path2mysql.po.Full_img_unzip_2014_2015_fill_PDF_PATH;
import com.sixlengs.path2mysql.util.ConnectionFactory;
import com.sixlengs.path2mysql.util.ListSplitUtils;
import com.sixlengs.path2mysql.util.TimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * BelongsProject: 中汽知投大数据
 *
 * @author wb, xing
 * CreateTime: 2021/4/19 10:13
 * Description:
 */
@Slf4j
public class Processor {
    static String directory;
    static String ip;
    static String database;
    static String table;
    static String username;
    static String password;
    static String suffix;
    private static Connection conn = null;
    static SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss");

//    private static PreparedStatement ps = null;
//    private static Logger logger = Logger.getLogger(Processor.class.getName());
//    private String sourceTable = "pdf_path_full_img_unzip_2014_2015_fill";

    public static void main(String[] args) {
        // 目录 后缀: PDF/* ip 库 表名 用户名 密码
        if (args != null && args.length == 7) {
            directory = args[0];
            // 相对路径替换为绝对路径   Windows 路径转义   去掉最后的 / , 如果有
            directory = dealPath(directory);
            suffix = args[1].toUpperCase();
            ip = args[2];
            database = args[3];
            table = args[4];
            table = table +"_"+ format.format(new Date());
            username = args[5];
            password = args[6];
            // 初始化连接
            conn = ConnectionFactory.getConnection(ip, database, username, password);
            long nowStart = System.currentTimeMillis();
            // 建表
            createTable();
            log.info("任务分析: 解析【{}】目录下, 后缀为:{} (*代表所有文件) 的文件绝对路径写入mysql: ip{}  {}库下{}表  用户名:{} 密码:{}", directory, suffix, ip, database, table, username, password);

//            // 第一层遍历 , 分解下任务,避免文件过多,一直卡死
            File[] files = new File(directory).listFiles();
            for (File fileSon : files) {
                List<String> pathList = getAllFilePath(fileSon, new ArrayList<String>());
                List<Full_img_unzip_2014_2015_fill_PDF_PATH> list = pathList.stream().map(s -> {
                    return new Full_img_unzip_2014_2015_fill_PDF_PATH(s);
                }).collect(Collectors.toList());

                save2mysql(list);
                log.info("------任务结束------- 当前时间{}  花费时间{} \n\n", TimeUtils.format(new Date()), TimeUtils.longDiffFormat(nowStart, System.currentTimeMillis()));
                System.exit(0);
            }
        } else {
            System.out.println("参数错误:  目录 后缀: PDF/* ip 库 表名 用户名 密码");
            System.exit(0);
        }
    }

    // 相对路径替换为绝对路径   Windows 路径转义   去掉最后的 / , 如果有
    public static String dealPath(String path) {
        path = new File(path).getAbsolutePath().replace("\\", "/");
        return subs(path);
    }

    // 删除路径最后的斜杠
    public static String subs(String s) {
        if (s.lastIndexOf("/") == s.length()) {
            return s.substring(0, s.lastIndexOf("/"));
        }
        return s;
    }

    public static List<String> getAllFilePath(File file, List<String> filePathList) {
        if (file != null) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (File fileSon : files) {
                    getAllFilePath(fileSon, filePathList);
                }
            } else if (file.isFile()) {
                // 未指定后缀
                if ("*".equals(suffix)) {
                    filePathList.add(file.getAbsolutePath().replace("\\", "/"));

                }
                // 指定后缀 , 大写比较
                else {
                    if (file.getAbsolutePath().toUpperCase().endsWith(suffix)) {
                        filePathList.add(file.getAbsolutePath().replace("\\", "/"));
                    }
                }
            }
        }
        return filePathList;
    }

    public static void createTable() {
        try {
            PreparedStatement preparedStatement = conn.prepareStatement(StrUtil.format("create table {}  (" +
                    "  `num` int(8) NOT NULL AUTO_INCREMENT," +
                    "  `file_path` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL," +
                    "  PRIMARY KEY (`num`) USING BTREE" +
                    ") ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;", table));
            preparedStatement.execute();
        } catch (SQLException e) {
            log.error("建表失败 {}.{}", database, table, e);
        }
    }

    public static void save2mysql(List<Full_img_unzip_2014_2015_fill_PDF_PATH> pdfList) {
        try {
            PreparedStatement ps;
            ps = conn.prepareStatement(StrUtil.format("insert into {} values (null,?)", table));
            List<List<Full_img_unzip_2014_2015_fill_PDF_PATH>> lists = ListSplitUtils.subListByNum(pdfList, 1000);
            for (List<Full_img_unzip_2014_2015_fill_PDF_PATH> list : lists) {
                // 批次写入 1000条
                log.info("PDF路径写入mysql,当前批次【{}】", list.size());
                for (Full_img_unzip_2014_2015_fill_PDF_PATH bean : list) {
//                    ps.setString(1, bean.getFileName());
                    ps.setString(1, bean.getAbsolutePath());

                    ps.addBatch();
                    ps.executeBatch();
                    conn.setAutoCommit(false);
                    conn.commit();
                    ps.clearBatch();
                }
            }
            ps.close();

        } catch (Exception e) {
            log.error("写入数据库错误", e);
        }
    }

}
