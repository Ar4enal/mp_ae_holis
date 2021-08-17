uniform mat4 uMVPMatrix;
attribute vec3 position;
attribute vec2 inputTextureCoordinate;
varying vec2 vTexCoord;
void main(){
	gl_Position = vec4(position,1.0);
	vec4 texCoordTemp = uMVPMatrix*vec4(inputTextureCoordinate,1.0,1.0);
	vTexCoord = vec2(texCoordTemp.x,texCoordTemp.y);
}