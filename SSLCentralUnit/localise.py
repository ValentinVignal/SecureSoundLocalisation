import numpy as np

class Source:
    def __init__(self,x,y):
        self.x=x
        self.y=y

# Global variables
source_0=Source(0,0)
source_1=Source(1,0)
source_2=Source(0,1)
c=300 #speed of the sound (m/s)

def get_coordinates(t0,t1,t2):
    d0,d1,d2=c*t0,c*t1,c*t2
    coord_prev=np.matrix('0;0')
    def f(coord): #compute the matrix of the 3 position function (we want to solve f(x)=0)
        x,y=coord[0,0],coord[1,0]
        f=np.zeros(shape=(3,1))
        f[0]=(x-source_0.x)**2+(y-source_0.y)**2-d0**2
        f[1]=(x-source_1.x)**2+(y-source_1.y)**2-d1**2
        f[2]=(x-source_2.x)**2+(y-source_2.y)**2-d2**2
        return np.asmatrix(f)
    def get_jac(coord): #compute the corresponding jacobian matrix
        x,y=coord[0],coord[1]
        jac=np.zeros(shape=(3,2))
        jac[0,0]=x-source_0.x
        jac[1,0]=x-source_1.x
        jac[2,0]=x-source_2.x
        jac[0,1]=y-source_0.y
        jac[1,1]=y-source_1.y
        jac[2,1]=y-source_2.y
        return 2*np.asmatrix(jac)
    jac=get_jac(coord_prev)
    coord=coord_prev-(jac.T*jac)**(-1)*jac.T*f(coord_prev)
    while np.linalg.norm(coord-coord_prev)>0.001:
        coord_prev=coord
        jac=get_jac(coord_prev)
        coord=coord_prev-(jac.T*jac)**(-1)*jac.T*f(coord_prev)
    return coord[0,0],coord[1,0]

#run some tests to verify
x,y=0.4,0.9
t0=((x-source_0.x)**2 + (y-source_0.y)**2)**(1/2)/300
t1=((x-source_1.x)**2 + (y-source_1.y)**2)**(1/2)/300
t2=((x-source_2.x)**2 + (y-source_2.y)**2)**(1/2)/300
x_comp,y_comp=get_coordinates(t0,t1,t2)
print(f"theoretical coord: ({x},{y})")
print(f"computed coord: ({x_comp},{y_comp})")
