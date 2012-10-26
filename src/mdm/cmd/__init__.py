
# these "modules" all contain one function, with the same name as the module.
#  thus, when imported, they overwrite the module reference themselves...
#   and you end up being able to call any of the functions as `mdm.cmd.thefunction()`
#    while the functions themselves are broken out into their own files.

from mdm.cmd.status		import *;
from mdm.cmd.update		import *;
from mdm.cmd.add		import *;
from mdm.cmd.alter		import *;
from mdm.cmd.remove		import *;
from mdm.cmd.release		import *;
from mdm.cmd.releaseinit	import *;


