package com.patrick.fintech.loan_backend.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class SecureFileUploadValidator {
    private static final Set<String> IMAGE_DOC_TYPES=Set.of("application/pdf","image/jpeg","image/png","image/webp");
    public void validateDocument(MultipartFile file,long maxBytes)throws IOException{
        if(file==null||file.isEmpty()) throw new IllegalArgumentException("No file was received.");
        if(file.getSize()>maxBytes) throw new IllegalArgumentException("Uploaded file exceeds the maximum allowed size.");
        String type=normalize(file.getContentType());
        if(!IMAGE_DOC_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported file type.");
        byte[] head; try(var input=file.getInputStream()){ head=input.readNBytes(16); }
        boolean valid=switch(type){
            case "application/pdf" -> starts(head,new byte[]{0x25,0x50,0x44,0x46});
            case "image/jpeg" -> starts(head,new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff});
            case "image/png" -> starts(head,new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a});
            case "image/webp" -> starts(head,new byte[]{0x52,0x49,0x46,0x46}) && ascii(head,8,12,"WEBP");
            default -> false;
        };
        if(!valid) throw new IllegalArgumentException("File content does not match its declared type.");
        String name=file.getOriginalFilename();
        if(name!=null){ if(name.length()>255) throw new IllegalArgumentException("Filename is too long."); if(name.contains("..")||name.contains("/")||name.contains("\\")||name.indexOf('\u0000')>=0) throw new IllegalArgumentException("Invalid filename."); }
    }
    private String normalize(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT).split(";")[0];}
    private boolean starts(byte[] a,byte[] b){if(a.length<b.length)return false;for(int i=0;i<b.length;i++)if(a[i]!=b[i])return false;return true;}
    private boolean ascii(byte[] a,int from,int to,String s){if(a.length<to)return false;return new String(a,from,to-from,StandardCharsets.US_ASCII).equals(s);}
}
