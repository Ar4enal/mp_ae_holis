#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTexCoord;
uniform int stereoARMode;//0-单目VR或平面模式, 1-双目VR模式, 2-单目AR模式, 3-双目AR模式
uniform sampler2D sampler2dState;//显示连接状态
uniform float eyeA;
uniform vec2 distortioPara;

void main() {
      if(stereoARMode == 0 || stereoARMode == 2){
         gl_FragColor = texture2D(sampler2dState, vTexCoord);
      }else if(stereoARMode == 1 || stereoARMode == 3){
         float a = 5.0 / 1.33 * 0.441;
         float b = 17.0 / 1.33 * 0.156;
         float cx = 1.05;
         float cy = 0.95;
         vec4 centerPosition = vec4(0.525, 0.5, 0.525, 0.5);
         float distanceValue1 = pow(vTexCoord.x / 4.0 * 3.0 + 0.1 - centerPosition[0], 2.0) + pow((vTexCoord.y - centerPosition[1]) / 2.0, 2.0);
         float distanceValue2 = pow(vTexCoord.x / 4.0 * 3.0 + 0.2 - centerPosition[2], 2.0) + pow((vTexCoord.y - centerPosition[3]) / 2.0, 2.0);
         float coef1 = 1.0 + a * distanceValue1 + b * pow(distanceValue1, 2.0);
         float coef2 = 1.0 + a * distanceValue2 + b * pow(distanceValue2, 2.0);

         float xLeft = (vTexCoord.x / 4.0 * 3.0 + 0.1 -  centerPosition[0]) * coef1 * cx + centerPosition[0];
         float yLeft = (vTexCoord.y - centerPosition[1]) * coef1 * cy + centerPosition[1];
         float xRight = (vTexCoord.x / 4.0 * 3.0 + 0.2 - centerPosition[2]) * coef2 * cx + centerPosition[2];
         float yRight = (vTexCoord.y - centerPosition[3]) * coef2 * cy + centerPosition[3];
         float x = (1.0-eyeA)*xLeft + eyeA*xRight;
         float y = (1.0-eyeA)*yLeft + eyeA*yRight;
         float p = step(x,0.0)+step(1.0,x)+step(y,0.0)+step(1.0,y);
         gl_FragColor = p*vec4(0.0,0.0,0.0,0.0)+(1.0-p)*texture2D(sampler2dState, vec2(x, y));
      }
}