
# coding: utf-8

# # Label the Stable Prototypes 
# Preparations for using DMSP

# In[ ]:

from __future__ import print_function
import pandas as pd
import os
import itertools
import sys
from qmpy import io
from fpassmgr.prototypes import PrototypeLibraryFactory
from pymatgen import Composition
from pymatgen.phasediagram.maker import PhaseDiagram
from pymatgen.phasediagram.analyzer import PDAnalyzer
from pymatgen.phasediagram.entries import PDEntry
import pickle as pkl


# ## Load in the QHs

def load_dataset(source):
    d = pd.read_csv(os.path.join('..', 'datasets', source, 'properties.txt'), delim_whitespace=True)
    d['source'] = source
    d['composition'] = d['filename'].apply(lambda x: Composition(x.split("-")[-1]))
    return d


qh_data = load_dataset('quat-heuslers')


print('Loaded %d OQMD entries. %d are missing stabilities'%(len(qh_data), qh_data['stability'].isnull().sum()))


# Get element list

heusler_elems = set(sum([x.keys() for x in qh_data['composition']], []))
print('%d elements in QH set'%len(heusler_elems))


# ## Get the Stable OQMD/Heusler Entries
# These are going to serve as the prototype library for DMSP

all_oqmd = pd.concat([
    load_dataset(x) for x in ['oqmd-no-heusler', 'heuslers']
])
all_oqmd['id'] = list(range(len(all_oqmd)))
all_oqmd.set_index('id', inplace=True)
all_oqmd['stability'] = all_oqmd['stability'].convert_objects(convert_numeric=True)
print('Loaded %d OQMD entries. %d are missing stabilities'%(len(all_oqmd), all_oqmd['stability'].isnull().sum()))


def contains_only_QH_elements(comp):
    return all([x in heusler_elems for x in comp.keys()])


all_oqmd = all_oqmd[all_oqmd['composition'].apply(contains_only_QH_elements)]
print('%d contain only QH elements'%len(all_oqmd))


# ## Find the stable entries
# Some of the stabilities are missing. Use Pymatgen to get them

# Label the elements

all_oqmd['system'] = all_oqmd['composition'].apply(lambda x: "-".join(sorted([y.symbol for y in x.keys()])))


# Compute stability for each system

def get_pdentry(row, attribute=None):
    comp = Composition(row['filename'].split("-")[-1])
    return PDEntry(comp.fractional_composition, row['delta_e'], comp.reduced_formula,
                   os.path.join('..', 'datasets', row['source'], row['filename']))
all_oqmd['pdentry'] = all_oqmd.apply(lambda x: get_pdentry(x), axis=1)

def get_data_from_system(data, system):
    """Extract rows from a pandas array that are in a certain phase diagram
    
    :param data: DataFrame, data from which to query. Must contain column "system"
    :param system: list/set, list of elements to serve as input
    :return: DataFrame, with only entries that exclusively contain these elements"""
    
    # Get the systems that make up this phase diagram
    constit_systems = set()
    for sys in itertools.product(system, repeat=len(system)):
        constit_systems.add('-'.join(sorted(set(sys))))
    
    # Get all points that are at any of those systems
    query_str = ' or '.join(['system == "%s"'%s for s in constit_systems])
    return data.query(query_str)


def compute_stability(data):
    """Compute the stability of all entries
    
    :param data: dataframe, data to be assesseed
    :return: stabilities for each entry"""
    
    pdg = PhaseDiagram(data['pdentry'])
    pda = PDAnalyzer(pdg)
    
    return [pda.get_e_above_hull(x) for x in data['pdentry'] ]


# Compute the phase diagram

n_systems = len(set(all_oqmd[all_oqmd['stability'].isnull()]['system']))
for i, (gid, group) in enumerate(all_oqmd[all_oqmd['stability'].isnull()].groupby('system')):
    my_data = get_data_from_system(all_oqmd, gid.split('-'))
    
    all_oqmd.loc[my_data.index, 'stability'] = compute_stability(my_data)
    print('\r%d/%d - %s'%(i + 1, n_systems, gid), end="")
print()


# Get all the stable entries

all_oqmd.query('stability <= 0', inplace=True)
print('There are %d stable entries in the OQMD'%len(all_oqmd))


# ## Find the prototypes

def get_path(entry):
    return os.path.join('..', 'datasets', entry['source'], entry['filename'].rstrip())


# Run the test
prototype_dict = {}
failure_count = 0
for i,(_,entry) in enumerate(all_oqmd.iterrows()):
    try:
        PrototypeLibraryFactory.add_structure(prototype_dict, io.poscar.read(get_path(entry)))
    except:
        print('Entry failed: ' + entry['filename'], file=sys.stderr)
    print('\r%d/%d'%(i + 1, len(all_oqmd)), end="")
    sys.stdout.flush()
print('Identified %d prototypes. Errors=%d'%(len(prototype_dict), failure_count))



# Save them
PrototypeLibraryFactory.save_to_directory(prototype_dict, 'prototypes')

with open('oqmd_prototypes.list', 'w') as fp:
    for p,exs in prototype_dict.items():
        for ex in exs:
            print('%s %s'%(ex, p.name), file=fp)


