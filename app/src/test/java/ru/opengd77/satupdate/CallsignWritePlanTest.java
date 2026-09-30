package ru.opengd77.satupdate;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class CallsignWritePlanTest {
    private static class Memory implements CallsignWritePlan.Memory {
        final byte[] bytes=new byte[0xe00000];final List<Integer> writes=new ArrayList<>();boolean backup;
        int fail=-1;
        Memory(){Arrays.fill(bytes,(byte)0x6a);}
        public byte[] read(int a,int n){return Arrays.copyOfRange(bytes,a,a+n);}
        public void write(int a,byte[] b)throws IOException{assertTrue("Backup before write",backup);writes.add(a);if(a==fail)throw new IOException("interrupted");System.arraycopy(b,0,bytes,a,b.length);}
    }
    private CallsignDatabase db(int count)throws IOException {StringBuilder csv=new StringBuilder("ID,CALLSIGN\n");for(int i=0;i<count;i++)csv.append(4010000+i).append(",UN1A\n");return CallsignDatabase.read(new StringReader(csv.toString()),new CallsignDatabase.Options("",new boolean[5]," ",16));}
    @Test public void replacementHeaderLastAndNoOutsideChanges()throws Exception {
        Memory m=new Memory();byte[] before=m.bytes.clone();CallsignDatabase db=db(17476);
        List<CallsignWritePlan.Sector> plan=CallsignWritePlan.prepare(db,m,t->{});
        assertEquals(65,plan.size());CallsignWritePlan.execute(plan,m,s->{m.backup=true;assertEquals(65,s.size());},t->{});
        assertEquals(Integer.valueOf(CallsignDatabase.BASE),m.writes.get(0));assertEquals(Integer.valueOf(CallsignDatabase.BASE),m.writes.get(m.writes.size()-1));
        assertArrayEquals(db.first,m.read(CallsignDatabase.BASE,db.first.length));assertArrayEquals(db.second,m.read(CallsignDatabase.BASE1,db.second.length));
        for(int i=0;i<m.bytes.length;i++)if(!(i>=CallsignDatabase.BASE&&i<CallsignDatabase.BASE+db.first.length)&&!(i>=CallsignDatabase.BASE1&&i<CallsignDatabase.BASE1+db.second.length))assertEquals("outside "+i,before[i],m.bytes[i]);
        int writes=m.writes.size();assertFalse(CallsignWritePlan.execute(CallsignWritePlan.prepare(db,m,t->{}),m,s->{fail("No backup for no-op");},t->{}));assertEquals(writes,m.writes.size());
    }
    @Test public void backupFailureOrStaleReadNeverWrites()throws Exception {
        Memory m=new Memory();List<CallsignWritePlan.Sector> p=CallsignWritePlan.prepare(db(2),m,t->{});
        try{CallsignWritePlan.execute(p,m,s->{throw new IOException("disk full");},t->{});fail();}catch(IOException expected){}assertTrue(m.writes.isEmpty());
        m.bytes[CallsignDatabase.BASE+300]++;
        try{CallsignWritePlan.execute(p,m,s->{m.backup=true;},t->{});fail();}catch(IOException expected){}assertTrue(m.writes.isEmpty());
    }
    @Test public void interruptedMultiSectorWriteDoesNotPublishHeader()throws Exception {
        Memory m=new Memory();m.fail=CallsignDatabase.BASE+4096;
        try{CallsignWritePlan.execute(CallsignWritePlan.prepare(db(600),m,t->{}),m,s->{m.backup=true;},t->{});fail();}catch(IOException expected){}
        assertEquals(0,m.bytes[CallsignDatabase.BASE]);assertEquals(0,m.bytes[CallsignDatabase.BASE+1]);assertEquals(2,m.writes.size());
    }
    @Test public void readbackMismatchStopsWrite()throws Exception {
        Memory m=new Memory(){public void write(int a,byte[] b)throws IOException{super.write(a,b);bytes[a+100]^=1;}};
        try{CallsignWritePlan.execute(CallsignWritePlan.prepare(db(2),m,t->{}),m,s->{m.backup=true;},t->{});fail();}catch(IOException e){assertTrue(e.getMessage().contains("read-back"));}
    }
    @Test public void hardwareGatesAndProtectedAddresses()throws Exception {
        CallsignWritePlan.checkRadio(5,4,0x4018);
        for(long[] args:new long[][]{{6,4,0x4018},{5,2,0x4018},{5,4,0x4017}})try{CallsignWritePlan.checkRadio(args[0],args[1],args[2]);fail();}catch(IOException expected){}
        Memory m=new Memory();List<CallsignWritePlan.Sector> plan=CallsignWritePlan.prepare(db(1),m,t->{});plan.add(new CallsignWritePlan.Sector(0xa0000,new byte[4096],new byte[4096]));
        try{CallsignWritePlan.execute(plan,m,s->{fail();},t->{});fail();}catch(IOException expected){}assertTrue(m.writes.isEmpty());
    }
}
