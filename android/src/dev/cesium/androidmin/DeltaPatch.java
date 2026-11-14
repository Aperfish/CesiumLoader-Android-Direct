package dev.cesium.androidmin;

import java.io.*;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

/** Checked BSDIFF control streams transported with standard zlib compression. */
public final class DeltaPatch {
    public static byte[] apply(byte[] original, byte[] patch) throws IOException {
        if(patch.length<36 || !Arrays.equals(Arrays.copyOf(patch,8),"CSDIFF01".getBytes("US-ASCII")))throw new IOException("补丁格式错误");
        long oldLength=unsigned(patch,8,8),newLength=unsigned(patch,16,8);
        int controls=size(patch,24),diffLength=size(patch,28),extraLength=size(patch,32);
        if(oldLength!=original.length||newLength<1||newLength>64*1024*1024L||controls%24!=0||controls>16*1024*1024||diffLength>newLength||extraLength>newLength)throw new IOException("补丁长度不合法");
        int expected=Math.addExact(controls,Math.addExact(diffLength,extraLength));
        byte[] decoded=new byte[expected];
        try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(patch,36,patch.length-36))) {
            int offset=0,n;while(offset<decoded.length&&(n=in.read(decoded,offset,decoded.length-offset))!=-1)offset+=n;
            if(offset!=decoded.length||in.read()!=-1)throw new IOException("补丁流长度不符");
        }
        byte[] result=new byte[(int)newLength];
        int cp=0,dp=controls,ep=controls+diffLength,np=0;
        long op=0;
        while(np<result.length) {
            if(cp+24>controls)throw new IOException("补丁控制流不足");
            long x=offset(decoded,cp),y=offset(decoded,cp+8),z=offset(decoded,cp+16);cp+=24;
            if(x<0||y<0||x+y>result.length-(long)np||x>controls+diffLength-(long)dp||y>decoded.length-(long)ep)throw new IOException("补丁越界");
            for(int i=0;i<(int)x;i++) {
                int v=decoded[dp+i]&255;long source=op+i;
                if(source>=0&&source<original.length)v+=(original[(int)source]&255);
                result[np+i]=(byte)v;
            }
            np+=(int)x;dp+=(int)x;op+=x;
            System.arraycopy(decoded,ep,result,np,(int)y);np+=(int)y;ep+=(int)y;op+=z;
        }
        if(cp!=controls||dp!=controls+diffLength||ep!=decoded.length)throw new IOException("补丁存在未使用的数据");
        return result;
    }
    private static int size(byte[] b,int p)throws IOException{long n=unsigned(b,p,4);if(n>Integer.MAX_VALUE)throw new IOException("长度溢出");return(int)n;}
    private static long unsigned(byte[] b,int p,int n){long v=0;for(int i=n-1;i>=0;i--)v=(v<<8)|(b[p+i]&255L);return v;}
    private static long offset(byte[] b,int p){long v=b[p+7]&127;for(int i=6;i>=0;i--)v=(v<<8)|(b[p+i]&255L);return(b[p+7]&128)==0?v:-v;}
}
