package dev.cesium.androidmin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal UnityFS/LZ4 and serialized TextAsset editor. No native library or ABI dependency. */
final class UnityBundle {
    private static final int LIMIT=64*1024*1024;
    private final int version;
    private final String player,engine;
    private final List<Node> nodes=new ArrayList<>();
    private Asset runtimeAsset;
    private Node runtimeNode;
    private Item runtimeItem;
    private static final class Node {
        String name;int flags;byte[] bytes;
        Node(String name,int flags,byte[] bytes){this.name=name;this.flags=flags;this.bytes=bytes;}
    }
    private static final class Item {int offsetField,sizeField,offset,size,type;}
    private static final class Reader {
        final byte[] bytes;final boolean little;int p;
        Reader(byte[] bytes,boolean little){this.bytes=bytes;this.little=little;}
        void need(int n)throws IOException{if(n<0||p<0||p>bytes.length-n)throw new IOException("Unity 文件不完整或长度无效");}
        void skip(int n)throws IOException{need(n);p+=n;}
        int u8()throws IOException{need(1);return bytes[p++]&255;}
        long number(int n)throws IOException {
            need(n);long value=0;
            if(little)for(int i=n-1;i>=0;i--)value=(value<<8)|(bytes[p+i]&255L);
            else for(int i=0;i<n;i++)value=(value<<8)|(bytes[p+i]&255L);
            p+=n;return value;
        }
        int size()throws IOException{return bound(number(4));}
        String zero()throws IOException {
            int start=p;while(u8()!=0)if(p-start>4096)throw new IOException("Unity 字符串过长");
            return new String(bytes,start,p-start-1,StandardCharsets.UTF_8);
        }
        void align(int n)throws IOException{skip((n-p%n)%n);}
        byte[] take(int n)throws IOException{need(n);byte[] result=Arrays.copyOfRange(bytes,p,p+n);p+=n;return result;}
    }
    private static int bound(long n)throws IOException{if(n<0||n>LIMIT)throw new IOException("Unity 文件超出兼容编辑范围");return(int)n;}
    private static byte[] decompress(byte[] bytes,int length,int flags)throws IOException {
        int mode=flags&63;
        if(mode==0){if(bytes.length!=length)throw new IOException("Unity 块长度不符");return bytes;}
        if(mode!=2&&mode!=3)throw new IOException("实验兼容暂不支持此 Unity 压缩类型："+mode);
        byte[] out=new byte[bound(length)];int ip=0,op=0;
        while(ip<bytes.length) {
            int token=bytes[ip++]&255,literals=token>>>4;
            if(literals==15){int n;do{if(ip==bytes.length)throw new IOException("LZ4 literal 不完整");n=bytes[ip++]&255;literals=Math.addExact(literals,n);}while(n==255);}
            if(literals>bytes.length-ip||literals>out.length-op)throw new IOException("LZ4 literal 越界");
            System.arraycopy(bytes,ip,out,op,literals);ip+=literals;op+=literals;
            if(ip==bytes.length)break;
            if(ip+2>bytes.length)throw new IOException("LZ4 offset 不完整");
            int distance=(bytes[ip]&255)|((bytes[ip+1]&255)<<8);ip+=2;
            if(distance==0||distance>op)throw new IOException("LZ4 offset 无效");
            int count=(token&15)+4;
            if((token&15)==15){int n;do{if(ip==bytes.length)throw new IOException("LZ4 match 不完整");n=bytes[ip++]&255;count=Math.addExact(count,n);}while(n==255);}
            if(count>out.length-op)throw new IOException("LZ4 match 越界");
            for(int i=0;i<count;i++)out[op+i]=out[op+i-distance];op+=count;
        }
        if(op!=length)throw new IOException("LZ4 解压长度不符");return out;
    }
    UnityBundle(byte[] input)throws IOException {
        Reader r=new Reader(input,false);
        if(!r.zero().equals("UnityFS"))throw new IOException("候选缓存不是 UnityFS");
        version=r.size();if(version<6||version>8)throw new IOException("不支持的 UnityFS 版本："+version);
        player=r.zero();engine=r.zero();long declared=r.number(8);
        if(declared!=input.length)throw new IOException("UnityFS 总长度不符");
        int packedInfo=r.size(),plainInfo=r.size(),flags=(int)r.number(4);
        if((flags&0x400)!=0)throw new IOException("暂不支持加密的 Unity 缓存");
        if(version>=7||engine.startsWith("2019.4."))r.align(16);
        int dataPosition=r.p,infoPosition=(flags&128)!=0?input.length-packedInfo:r.p;
        r.p=infoPosition;byte[] info=decompress(r.take(packedInfo),plainInfo,flags);
        r.p=(flags&128)!=0?dataPosition:infoPosition+packedInfo;
        if((flags&512)!=0)r.align(16);
        Reader ir=new Reader(info,false);ir.skip(16);int blocks=ir.size();
        if(blocks>65536)throw new IOException("Unity 块数过多");
        int[] sizes=new int[blocks],packed=new int[blocks],modes=new int[blocks];int total=0;
        for(int i=0;i<blocks;i++){sizes[i]=ir.size();packed[i]=ir.size();modes[i]=(int)ir.number(2);total=bound((long)total+sizes[i]);}
        byte[] data=new byte[total];int pos=0;
        for(int i=0;i<blocks;i++){byte[] block=decompress(r.take(packed[i]),sizes[i],modes[i]);System.arraycopy(block,0,data,pos,block.length);pos+=block.length;}
        int count=ir.size();if(count>4096)throw new IOException("Unity 文件节点过多");
        for(int i=0;i<count;i++) {
            int offset=bound(ir.number(8)),length=bound(ir.number(8));int nodeFlags=(int)ir.number(4);String name=ir.zero();
            if(offset>data.length-length)throw new IOException("Unity 节点越界");
            Node node=new Node(name,nodeFlags,Arrays.copyOfRange(data,offset,offset+length));nodes.add(node);
            if((nodeFlags&4)!=0) {
                Asset asset=new Asset(node.bytes);
                for(Item item:asset.items)if(item.type==49&&asset.name(item).equals("AstralParty.Runtime.dll")) {
                    if(runtimeItem!=null)throw new IOException("发现多个游戏运行程序集");
                    runtimeAsset=asset;runtimeNode=node;runtimeItem=item;
                }
            }
        }
        if(runtimeItem==null)throw new IOException("缓存中没有 AstralParty.Runtime.dll");
    }
    byte[] runtime()throws IOException{return runtimeAsset.script(runtimeItem);}
    byte[] replaceRuntime(byte[] replacement)throws IOException {
        if(replacement.length<2||replacement[0]!='M'||replacement[1]!='Z')throw new IOException("基准运行程序集无效");
        runtimeNode.bytes=runtimeAsset.replace(runtimeItem,replacement);
        // Store plain blocks: Android/Unity accepts this, and no platform compressor is required.
        ByteArrayOutputStream data=new ByteArrayOutputStream();int[] offsets=new int[nodes.size()];
        for(int i=0;i<nodes.size();i++){offsets[i]=data.size();data.write(nodes.get(i).bytes);}
        byte[] body=data.toByteArray();bound(body.length);
        ByteArrayOutputStream infoBytes=new ByteArrayOutputStream();DataOutputStream info=new DataOutputStream(infoBytes);
        info.write(new byte[16]);int blocks=(body.length+131071)/131072;info.writeInt(blocks);
        for(int i=0;i<blocks;i++){int n=Math.min(131072,body.length-i*131072);info.writeInt(n);info.writeInt(n);info.writeShort(0);}
        info.writeInt(nodes.size());
        for(int i=0;i<nodes.size();i++){Node n=nodes.get(i);info.writeLong(offsets[i]);info.writeLong(n.bytes.length);info.writeInt(n.flags);zero(info,n.name);}
        byte[] metadata=infoBytes.toByteArray();
        ByteArrayOutputStream headerBytes=new ByteArrayOutputStream();DataOutputStream header=new DataOutputStream(headerBytes);
        zero(header,"UnityFS");header.writeInt(version);zero(header,player);zero(header,engine);
        int sizePosition=headerBytes.size();header.writeLong(0);header.writeInt(metadata.length);header.writeInt(metadata.length);header.writeInt(64);
        if(version>=7||engine.startsWith("2019.4."))while(headerBytes.size()%16!=0)header.writeByte(0);
        byte[] prefix=headerBytes.toByteArray();put(prefix,sizePosition,(long)prefix.length+metadata.length+body.length,8,false);
        ByteArrayOutputStream result=new ByteArrayOutputStream();result.write(prefix);result.write(metadata);result.write(body);
        byte[] output=result.toByteArray();bound(output.length);
        if(!Arrays.equals(new UnityBundle(output).runtime(),replacement))throw new IOException("重建缓存验证失败");
        return output;
    }
    private static void zero(DataOutputStream out,String value)throws IOException{out.write(value.getBytes(StandardCharsets.UTF_8));out.writeByte(0);}
    private static void put(byte[] bytes,int pos,long value,int size,boolean little) {
        for(int i=0;i<size;i++){bytes[pos+(little?i:size-1-i)]=(byte)value;value>>>=8;}
    }
    private static final class Asset {
        final byte[] bytes;final boolean little;final int version,dataOffset;final List<Item> items=new ArrayList<>();
        Asset(byte[] bytes)throws IOException {
            this.bytes=bytes;Reader header=new Reader(bytes,false);header.skip(8);version=header.size();dataOffset=readDataOffset(header,version);
            if(version<17||version>22)throw new IOException("暂不支持此序列化资源版本："+version);
            little=bytes[16]==0;
            Reader r=new Reader(bytes,little);r.p=version>=22?48:20;r.zero();r.skip(4);boolean tree=r.u8()!=0;
            int count=r.size();if(count>65536)throw new IOException("Unity 类型数过多");int[] classes=new int[count];
            for(int i=0;i<count;i++) {
                int cls=(int)r.number(4);classes[i]=cls;r.skip(1+2);
                if(cls==114)r.skip(16);r.skip(16);
                if(tree) {
                    int nodes=r.size(),strings=r.size();if(nodes>100000)throw new IOException("Unity 类型树过大");
                    r.skip(bound((long)nodes*(version>=19?32:24)));r.skip(strings);
                    if(version>=21){int deps=r.size();r.skip(bound((long)deps*4));}
                }
            }
            int objects=r.size();if(objects>100000)throw new IOException("Unity 对象数过多");
            for(int i=0;i<objects;i++) {
                r.align(4);r.skip(8);Item item=new Item();item.offsetField=r.p;item.offset=bound(r.number(version>=22?8:4));item.sizeField=r.p;item.size=r.size();
                int type=r.size();if(type>=classes.length)throw new IOException("Unity 对象类型无效");item.type=classes[type];
                if(item.offset>bytes.length-dataOffset-item.size)throw new IOException("Unity 对象越界");items.add(item);
            }
            if(r.p>dataOffset)throw new IOException("Unity 对象表覆盖数据区");
        }
        private static int readDataOffset(Reader r,int version)throws IOException {
            int offset=r.size();r.skip(4);
            if(version>=22){r.skip(4+8);offset=bound(r.number(8));r.skip(8);}
            if(offset>r.bytes.length)throw new IOException("Unity 数据区越界");return offset;
        }
        private Reader object(Item item){Reader r=new Reader(bytes,little);r.p=dataOffset+item.offset;return r;}
        String name(Item item)throws IOException {
            Reader r=object(item);int length=r.size();if(length>4096||length>item.size-4)throw new IOException("TextAsset 名称无效");
            return new String(r.take(length),StandardCharsets.UTF_8);
        }
        byte[] script(Item item)throws IOException {
            Reader r=object(item);r.skip(r.size());r.align(4);int length=r.size();
            if(length>dataOffset+item.offset+item.size-r.p)throw new IOException("TextAsset 程序集长度无效");return r.take(length);
        }
        byte[] replace(Item target,byte[] replacement)throws IOException {
            Reader r=object(target);r.skip(r.size());r.align(4);int lengthPosition=r.p;int oldLength=r.size();
            int prefix=r.p-(dataOffset+target.offset);int oldScriptEnd=r.p+oldLength;
            if(oldScriptEnd>dataOffset+target.offset+target.size)throw new IOException("TextAsset 长度越界");
            // TextAsset has only name and byte array; trailing bytes are alignment padding.
            int newSize=(prefix+replacement.length+3)&~3;
            byte[] object=new byte[newSize];System.arraycopy(bytes,dataOffset+target.offset,object,0,prefix);
            put(object,lengthPosition-dataOffset-target.offset,replacement.length,4,little);System.arraycopy(replacement,0,object,prefix,replacement.length);
            byte[] metadata=Arrays.copyOf(bytes,dataOffset);ByteArrayOutputStream data=new ByteArrayOutputStream();
            List<Item> sorted=new ArrayList<>(items);sorted.sort(Comparator.comparingInt(i->i.offset));int previousEnd=0;
            for(Item item:sorted) {
                if(item.offset<previousEnd)throw new IOException("Unity 对象重叠");previousEnd=item.offset+item.size;
                while(data.size()%8!=0)data.write(0);
                put(metadata,item.offsetField,data.size(),version>=22?8:4,little);
                put(metadata,item.sizeField,item==target?object.length:item.size,4,little);
                if(item==target)data.write(object);else data.write(bytes,dataOffset+item.offset,item.size);
            }
            byte[] body=data.toByteArray();long newLength=(long)metadata.length+body.length;bound(newLength);
            put(metadata,version>=22?24:4,newLength,version>=22?8:4,false);
            ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(metadata);out.write(body);return out.toByteArray();
        }
    }
}
