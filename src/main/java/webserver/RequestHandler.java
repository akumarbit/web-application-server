package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import db.DataBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import model.User;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;


    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream();
             OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            BufferedReader bufferedReader = new BufferedReader
                    (new InputStreamReader(in, "UTF-8"));
            String httpDataLine = bufferedReader.readLine();
            if (httpDataLine == null)
                return;
            String[] tokens = httpDataLine.split(" ");
            String httpMethod = tokens[0];
            String url = tokens[1];
            log.info("url:" + url);
            log.info("firstline:" + httpDataLine);
            String urltype = analyseurl(url);
            int contentLength = 0;
            boolean logined = false;
            while (!httpDataLine.equals("")) {
                httpDataLine = bufferedReader.readLine();
                if (httpDataLine.contains("Content-Length")) {
                    log.info("content-lengththing:" +
                            Arrays.toString(httpDataLine.split(" ")));
                    contentLength = Integer.parseInt(httpDataLine.split(" ")[1]);
                }
                if (httpDataLine.contains("Cookie")) {
                    Map<String,String> cookieMap =  HttpRequestUtils.
                            parseCookies(httpDataLine.split(" ")[1]);
                    logined = Boolean.parseBoolean(cookieMap.get("logined"));
                    log.info("logined:" +logined);
                }
                log.info(httpDataLine);
            }
            DataOutputStream dos = new DataOutputStream(out);

            if (urltype.equals("filerequest")) {
                sendFileData(url,dos);
            }
            else if(urltype.equals("cssrequest"))
            {
                sendcssData(url,dos);
            }
            else if (urltype.equals("createuser")) {
                createUser(url,dos,httpMethod,bufferedReader,contentLength);
             }
            else if (urltype.equals("loginuser")) {
                loginUser(dos,bufferedReader,contentLength);
            }
            else if(urltype.equals("listuser"))
            {
                listUser(logined,dos);
            }
            else {
                byte[] body =  "Hello World".getBytes();
                response200Header(dos, body.length);
                responseBody(dos, body);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void listUser(boolean logined,DataOutputStream dos)
    {
        if(logined)
        {
            Collection<User> userList = DataBase.findAll();
            byte[] body = createTableofUsers(userList);
            response200Header(dos, body.length);
            responseBody(dos, body);
        }
        else {
            response302Header(dos, "/user/login.html",false,
                    null);
        }
    }

    private byte[] createTableofUsers(Collection<User> userList)
    {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<table>");
        stringBuilder.append("<tr>");
        stringBuilder.append("<th>Userid</th>");
        stringBuilder.append("<th>Name</th>");
        stringBuilder.append("<th>Email</th>");
        for(User u: userList)
        {
            stringBuilder.append("<tr>");
            stringBuilder.append("<td>"+u.getUserId()+"</td>");
            stringBuilder.append("<td>"+u.getName()+"</td>");
            stringBuilder.append("<td>"+u.getEmail()+"</td>");
            stringBuilder.append("<tr>");
        }
        stringBuilder.append("<table>");
        return stringBuilder.toString().getBytes();
    }
    private void loginUser(DataOutputStream dos,BufferedReader bufferedReader,int contentLength)
            throws IOException {
        String params = null;
        try {
            params = IOUtils.readData(bufferedReader, contentLength);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> paramsMap = HttpRequestUtils.parseQueryString(params);
        User dbUser = DataBase.findUserById(paramsMap.get("userId"));
        if(dbUser!= null && (dbUser.getUserId().equals(paramsMap.get("userId"))) &&
                (dbUser.getPassword().equals(paramsMap.get("password"))))
        {
            log.info("user credentials matched with saved user in db");
            response302Header(dos, "/index.html",true,
                    "logined=true");
        }
        else{
            response302Header(dos, "/user/login_failed.html",true,
                    "logined=false");
        }
    }
    private void createUser(String url,DataOutputStream dos,String httpMethod,
                            BufferedReader bufferedReader,int contentLength)
            throws IOException{
        User webUser = null;
        String params = "";
        if (httpMethod.equals("GET")) {
            int index = url.indexOf("?");
            params = url.substring(index + 1);
        } else if (httpMethod.equals("POST")) {
            params = IOUtils.readData(bufferedReader, contentLength);

        }
        Map<String, String> paramsMap = HttpRequestUtils.parseQueryString(params);
        webUser = new User(paramsMap.get("userId"), paramsMap.get("password"),
                paramsMap.get("name"), paramsMap.get("email"));
        DataBase.addUser(webUser);
        log.info("new user created:" + webUser);
        response302Header(dos, "/index.html",false,null);

    }
    private void sendFileData(String url,DataOutputStream dos) throws IOException {
        byte[] body = new byte[0];
        try {
            body = Files.readAllBytes(new File("./webapp" +
                    url).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private void sendcssData(String url,DataOutputStream dos) throws IOException {
        byte[] body = new byte[0];
        try {
            body = Files.readAllBytes(new File("./webapp" +
                    url).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        response200Headercss(dos, body.length);
        responseBody(dos, body);
    }
    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Headercss(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String url,
                                   boolean setcookie, String cookievalue) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            if(setcookie)
            {
                dos.writeBytes("Set-Cookie: "+cookievalue+"\r\n");
            }
            dos.writeBytes("Location: " + url + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private String analyseurl(String url)
    {
        if(url.contains(".html") || url.contains(".ico"))
            return "filerequest";
        if(url.endsWith(".css"))
            return "cssrequest";
        if(url.startsWith("/user/create"))
            return "createuser";
        if(url.startsWith("/user/login"))
            return "loginuser";
        if(url.startsWith("/user/list"))
            return "listuser";

        return "helloworld";
    }
}
