import numpy as np


class Point:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    @property
    def norm2(self):
        return np.sqrt(np.float_power(self.x, 2) + np.float_power(self.y, 2))

    def __mul__(self, other):
        return Point(x=other * self.x,
                     y=other * self.y)

    def __add__(self, other):
        return Point(x=self.x + other.x,
                     y=self.y + other.y)

    def __sub__(self, other):
        return Point(x=self.x - other.x,
                     y=self.y - other.y)

    def __str__(self):
        return f'Point({self.x}, {self.y})'

    def copy(self):
        return Point(x=self.x,
                     y=self.y)


# Global variables
c = 340.29  # speed of the sound (m/s)


def create_diff_dist(offsets):
    diff_dist = []
    for i in range(len(offsets) - 1):
        for j in range(i + 1, len(offsets)):
            diff_dist.append((offsets[i] - offsets[j]) * c)
    return diff_dist


def get_coordinates(offsets, sources):
    """
    apply algorithm Gauss_Newton : https://fr.wikipedia.org/wiki/Algorithme_de_Gauss-Newton

    :param offsets:
    :param sources:

    :return:
    """
    nb_sources = len(sources)
    nb_combinaisons = int(nb_sources * (nb_sources - 1) / 2)
    diff_dist = create_diff_dist(offsets)
    coord = np.asmatrix(np.zeros(shape=(2, 1)))
    eps = 1e-8

    def combinaison_number(i, j):
        if i == j:
            raise Exception(f'i ({i}) can t be equal to j ({j})')
        i_ = min(i, j)
        j_ = max(i, j)
        res = int((nb_sources * i_) - (((i_ + 1) * i_) / 2) + j_ - i_ - 1)
        #print(f'i {i}, j {j}, nb sources {nb_sources}, combinaison number {res}')
        return res

    def f(coord):  # compute the matrix of the 3 position function (we want to solve f(x)=0)
        p = Point(coord[0, 0], coord[1, 0])
        f = np.zeros(shape=(nb_combinaisons, 1))
        for i in range(0, nb_sources - 1):
            for j in range(i + 1, nb_sources):
                c_n = combinaison_number(i, j)
                f[c_n] = ((p - sources[i]).norm2 - (p - sources[j]).norm2) - diff_dist[c_n]
        return np.asmatrix(f)

    def get_jac(coord):
        """
        compute the corresponding jacobian matrix : https://fr.wikipedia.org/wiki/Matrice_jacobienne#targetText=En%20analyse%20vectorielle%2C%20la%20matrice,vient%20du%20math%C3%A9maticien%20Charles%20Jacobi.

        :param coord:

        :return:
        """
        x, y = coord[0], coord[1]
        p = Point(x=x, y=y)
        jac = np.zeros(shape=(nb_combinaisons, 2))
        for i in range(0, nb_sources - 1):
            for j in range(i + 1, nb_sources):
                c_n = combinaison_number(i, j)
                jac[c_n, 0] = ((x - sources[i].x) / ((p - sources[i]).norm2 + eps)) - (
                        (x - sources[j].x) / ((p - sources[j]).norm2 + eps))
                jac[c_n, 1] = ((y - sources[i].y) / ((p - sources[i]).norm2 + eps)) - (
                        (y - sources[j].y) / ((p - sources[j]).norm2 + eps))
        return np.asmatrix(jac)

    for i in range(1000):
        jac = get_jac(coord)
        coord = coord - (jac.T * jac) ** (-1) * jac.T * f(coord)
    return coord[0, 0], coord[1, 0]


if __name__ == '__main__':
    # run some tests to verify

    # p coordinate :
    x, y = 2.5, 4
    point_p = Point(x, y)

    sources = [
        Point(0, 0),
        Point(5, 0),
        Point(0, 5)
    ]

    # Real offsets
    offsets = [(sources[i] - point_p).norm2 / c for i in range(len(sources))]

    # latency in reception
    latency = np.random.uniform(0, 1)  # in [0, 1[ second
    offsets_latency = [t + latency for t in offsets]

    x_comp, y_comp = get_coordinates(offsets_latency, sources)
    d = ((x - x_comp) ** 2 + (y - y_comp) ** 2) ** (1 / 2)
    print("With perfect time measures")
    print(f"theoretical coord: ({x},{y})")
    print(f"computed coord: ({x_comp:.5f},{y_comp:.5f})")
    print(f"error between positions: {d:.2f} meters")

    # Simulating errors on the times measured
    e = 0.001       # 1ms
    errors = [np.random.uniform(-e, e) for t in offsets]
    offsets_error = [offsets_latency[i] + errors[i] for i in range(len(offsets))]
    print(f"\nWith errors of 1ms on the times measured : {e} -> {errors}:\n\t{offsets_error} instead of {offsets_latency}")
    print(f"theoretical coord: ({x},{y})")
    x_comp, y_comp = get_coordinates(offsets_error, sources)
    d = ((x - x_comp) ** 2 + (y - y_comp) ** 2) ** (1 / 2)
    print(f"computed coord: ({x_comp:.5f},{y_comp:.5f})")
    print(f"error between positions: {d:.2f} meters")
