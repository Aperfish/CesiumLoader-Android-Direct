using System;
namespace CesiumLoader.SDK.Mobile {
 // No Unity calls: the gesture protocol is verified on the build host.
 public sealed class GestureState {
  bool tracking,blocked; int first,second; float x,y,span;
  public bool Consuming {get;private set;}
  public void Block(){blocked=true;tracking=false;Consuming=true;}
  public void Reset(){tracking=false;blocked=false;Consuming=false;}
  public bool Step(int count,int a,int b,float ax,float ay,float bx,float by,float scale,bool overUi,out float yaw,out float zoom){
   yaw=zoom=0;
   if(count==0){Reset();return false;}
   if(blocked){Consuming=true;return true;}
   if(count!=2){if(tracking||count>2)Block();return Consuming;}
   Consuming=true;
   if(overUi){Block();return true;}
   if(a>b){int id=a;a=b;b=id;float t=ax;ax=bx;bx=t;t=ay;ay=by;by=t;}
   float cx=(ax+bx)*.5f,cy=(ay+by)*.5f;
   float dx=bx-ax,dy=by-ay,dist=(float)Math.Sqrt(dx*dx+dy*dy);
   if(!tracking||first!=a||second!=b){first=a;second=b;x=cx;y=cy;span=dist;tracking=true;return true;}
   float s=Math.Max(1,scale);
   yaw=Math.Max(-30,Math.Min(30,(cx-x)/s*180f));
   zoom=Math.Max(-1,Math.Min(1,((dist-span)+(cy-y))/s*8f));
   x=cx;y=cy;span=dist;return true;
  }
 }
}
