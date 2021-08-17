#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform int stereoARMode;//0-单目VR或平面模式, 1-双目VR模式, 2-单目AR模式, 3-双目AR模式
uniform float eyeA;
varying vec2 vTexCoord;
uniform samplerExternalOES sampler2dVideo;//显示video画面
uniform vec2 distortioPara; //畸变系数
uniform vec2 xyDdistor;//xy方向畸变微调参数
uniform float eyeDistanceOffset;//瞳距调整参数(非cm单位)
uniform vec4 centerPosition; ////畸变双目中心点位置调整
void main() {
    if(stereoARMode == 0){ //0-单目VR或平面模式,
      gl_FragColor = texture2D(sampler2dVideo, vTexCoord);
    }else if(stereoARMode == 1){ // 1-双目VR模式
      float distanceValue1 = pow(vTexCoord.x/2.0-centerPosition[0],2.0)+pow((vTexCoord.y-centerPosition[1])/2.0,2.0);
      float distanceValue2 = pow(vTexCoord.x/2.0+0.5-centerPosition[2],2.0)+pow((vTexCoord.y-centerPosition[3])/2.0,2.0);
      float coef1 = 1.0+distortioPara.x*distanceValue1+distortioPara.y*pow(distanceValue1,2.0);
      float coef2 = 1.0+distortioPara.x*distanceValue2+distortioPara.y*pow(distanceValue2,2.0);

      float xLeft = (vTexCoord.x/2.0-centerPosition[0])*coef1+centerPosition[0]-eyeDistanceOffset;
      float yLeft = (vTexCoord.y-centerPosition[1])*coef1+centerPosition[1];
      float xRight = (vTexCoord.x/2.0+0.5-centerPosition[2])*coef2+centerPosition[2]+eyeDistanceOffset;
      float yRight = (vTexCoord.y-centerPosition[3])*coef2+centerPosition[3];
      float x = (1.0-eyeA)*xLeft + eyeA*xRight;
      float y = (1.0-eyeA)*yLeft + eyeA*yRight;
      float a = step(x,eyeA*0.5)+step(0.5+eyeA*0.5,x)+step(y,0.0)+step(1.0,y);
      gl_FragColor = a*vec4(0.0,0.0,0.0,1.0)+(1.0-a)*texture2D(sampler2dVideo, vec2(x,y));
    }else if(stereoARMode == 2){ //2-单目AR模式
      vec4 color = texture2D(sampler2dVideo, vec2(vTexCoord.x, vTexCoord.y / 2.0 + 0.5));
      vec4 color2 = texture2D(sampler2dVideo, vec2(vTexCoord.x, vTexCoord.y / 2.0));
      gl_FragColor = vec4(color2.r, color2.g, color2.b, color.r);
    }else if(stereoARMode == 3){//3-双目AR模式
      float distanceValue1 = pow(vTexCoord.x/2.0-centerPosition[0],2.0)+pow((vTexCoord.y-centerPosition[1])/2.0,2.0);
      float distanceValue2 = pow(vTexCoord.x/2.0+0.5-centerPosition[2],2.0)+pow((vTexCoord.y-centerPosition[3])/2.0,2.0);
      float coef1 = 1.0+distortioPara.x*distanceValue1+distortioPara.y*pow(distanceValue1,2.0);
      float coef2 = 1.0+distortioPara.x*distanceValue2+distortioPara.y*pow(distanceValue2,2.0);

      float xLeft = (vTexCoord.x/2.0-centerPosition[0])*coef1+centerPosition[0]-eyeDistanceOffset;
      float yLeft = (vTexCoord.y-centerPosition[1])*coef1+centerPosition[1];
      float xRight = (vTexCoord.x/2.0+0.5-centerPosition[2])*coef2+centerPosition[2]+eyeDistanceOffset;
      float yRight = (vTexCoord.y-centerPosition[3])*coef2+centerPosition[3];
      float x = (1.0-eyeA)*xLeft + eyeA*xRight;
      float y = (1.0-eyeA)*yLeft + eyeA*yRight;
      float a = step(x,eyeA*0.5)+step(0.5+eyeA*0.5,x)+step(y,0.0)+step(1.0,y);
      vec4 color = texture2D(sampler2dVideo, vec2(x, y / 2.0));
      vec4 color1 = texture2D(sampler2dVideo, vec2(x, y / 2.0 + 0.5));
      gl_FragColor = a*vec4(0.0,0.0,0.0,1.0)+(1.0-a)*vec4(color.r, color.g, color.b, color1.r);
    }
}